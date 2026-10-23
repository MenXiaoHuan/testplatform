package com.example.platform.runner.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.common.SensitiveDataRedactor;
import com.example.platform.repository.model.TestRepositoryEntity;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运行器工作空间服务实现 —— 基于 Git 克隆的工作空间管理。
 *
 * <p>私有仓库凭据来自仓库配置，而不是 gitUrl：
 * <ul>
 *   <li>HTTPS_TOKEN：使用临时 GIT_ASKPASS 脚本 + 环境变量，token 不进入命令行；</li>
 *   <li>SSH_KEY：解密后写入 0600 临时文件，通过 GIT_SSH_COMMAND 使用；</li>
 *   <li>PUBLIC：直接 clone。</li>
 * </ul>
 *
 * <p>clone 输出会交给 PREPARING 阶段日志，并对常见凭据做脱敏。
 */
@Service
public class RunnerWorkspaceServiceImpl implements RunnerWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(RunnerWorkspaceServiceImpl.class);
    private static final long MAX_CLONE_LOG_BYTES = 256L * 1024;
    private static final String AUTH_PUBLIC = "PUBLIC";
    private static final String AUTH_HTTPS_TOKEN = "HTTPS_TOKEN";
    private static final String AUTH_SSH_KEY = "SSH_KEY";

    private final Path workspaceRoot;
    private final int cloneTimeoutSeconds;
    private final RunnerProperties runnerProperties;
    private final CredentialCipher credentialCipher;

    public RunnerWorkspaceServiceImpl(RunnerProperties runnerProperties, CredentialCipher credentialCipher) {
        this.runnerProperties = runnerProperties;
        this.credentialCipher = credentialCipher;
        this.workspaceRoot = runnerProperties.getWorkspaceRoot() == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "playwright-platform", "workspaces")
                : runnerProperties.getWorkspaceRoot();
        this.cloneTimeoutSeconds = Math.max(10, runnerProperties.getGitCloneTimeoutSeconds());
    }

    @Override
    public Path prepareWorkspace(TestRepositoryEntity repository, String branch, Long taskId,
                                 Consumer<String> logConsumer) {
        GitUrlPolicy.validate(repository.getGitUrl());
        String safeBranch = GitUrlPolicy.validateBranch(branch);
        Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
        Path cloneLog = null;
        Path askPassFile = null;
        Path sshKeyFile = null;
        try {
            Files.createDirectories(workspaceRoot);
            deleteWorkspace(taskWorkspace);

            cloneLog = Files.createTempFile("platform-git-clone-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    buildCloneCommand(repository.getGitUrl(), safeBranch, taskWorkspace));
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(cloneLog.toFile());
            configureEnvironment(processBuilder.environment(), repository);

            String authType = normalizeAuthType(repository.getAuthType());
            if (AUTH_HTTPS_TOKEN.equals(authType)) {
                askPassFile = createAskPassScript();
                processBuilder.environment().put("GIT_ASKPASS", askPassFile.toString());
                processBuilder.environment().put("GIT_ASKPASS_REQUIRE", "force");
                processBuilder.environment().put("GIT_USERNAME",
                        defaultIfBlank(repository.getCredentialUsername(), "x-access-token"));
                processBuilder.environment().put("GIT_PASSWORD", decryptSecret(repository));
            } else if (AUTH_SSH_KEY.equals(authType)) {
                sshKeyFile = createSshKeyFile(decryptSecret(repository));
                processBuilder.environment().put("GIT_SSH_COMMAND", buildSshCommand(sshKeyFile));
            }

            Process process = processBuilder.start();
            boolean finished = process.waitFor(cloneTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                publishCloneOutput(cloneLog, logConsumer);
                throw new IllegalStateException("Git clone timed out after " + cloneTimeoutSeconds + " seconds");
            }
            publishCloneOutput(cloneLog, logConsumer);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Git clone failed with exit code "
                        + process.exitValue() + ": " + readTail(cloneLog));
            }
            return taskWorkspace;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git clone interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare workspace", exception);
        } finally {
            deleteQuietly(cloneLog);
            deleteQuietly(askPassFile);
            deleteQuietly(sshKeyFile);
        }
    }

    private List<String> buildCloneCommand(String gitUrl, String branch, Path taskWorkspace) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("clone");
        if (runnerProperties.isSubmodulesEnabled()) {
            command.add("--recurse-submodules");
            command.add("--shallow-submodules");
        }
        command.add("--depth");
        command.add("1");
        command.add("--single-branch");
        command.add("--branch");
        command.add(branch);
        command.add("--");
        command.add(gitUrl);
        command.add(taskWorkspace.toString());
        return command;
    }

    private void configureEnvironment(Map<String, String> environment, TestRepositoryEntity repository) {
        environment.put("GIT_TERMINAL_PROMPT", "0");
        if (!runnerProperties.isLfsEnabled()) {
            environment.put("GIT_LFS_SKIP_SMUDGE", "1");
        }
        String authType = normalizeAuthType(repository.getAuthType());
        if (AUTH_HTTPS_TOKEN.equals(authType) && !repository.getGitUrl().startsWith("https://")) {
            throw new IllegalArgumentException("HTTPS Token 认证只能用于 https:// 仓库地址");
        }
        if (AUTH_SSH_KEY.equals(authType)
                && !(repository.getGitUrl().startsWith("ssh://")
                || repository.getGitUrl().matches("^[\\w.-]+@[\\w.-]+:.+$"))) {
            throw new IllegalArgumentException("SSH Key 认证只能用于 ssh:// 或 git@host:path 仓库地址");
        }
    }

    private String buildSshCommand(Path sshKeyFile) {
        StringBuilder command = new StringBuilder("ssh -o BatchMode=yes")
                .append(" -i ").append(shellQuote(sshKeyFile.toString()))
                .append(" -o IdentitiesOnly=yes")
                .append(" -o StrictHostKeyChecking=yes");
        String knownHostsPath = runnerProperties.getKnownHostsPath();
        if (knownHostsPath != null && !knownHostsPath.isBlank()) {
            command.append(" -o UserKnownHostsFile=").append(shellQuote(knownHostsPath));
        }
        return command.toString();
    }

    private String decryptSecret(TestRepositoryEntity repository) {
        String secret = credentialCipher.decrypt(repository.getCredentialSecret());
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("私有仓库未配置有效凭据，请在仓库管理中重新保存");
        }
        return secret;
    }

    private Path createAskPassScript() throws IOException {
        Path script = Files.createTempFile("platform-git-askpass-", ".sh");
        String content = """
                #!/bin/sh
                case "$1" in
                  *Username*) printf '%s\\n' "$GIT_USERNAME" ;;
                  *Password*) printf '%s\\n' "$GIT_PASSWORD" ;;
                  *) printf '\\n' ;;
                esac
                """;
        Files.writeString(script, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        setPermissions(script, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return script;
    }

    private Path createSshKeyFile(String privateKey) throws IOException {
        Path keyFile = Files.createTempFile("platform-git-key-", ".key");
        Files.writeString(keyFile, privateKey.endsWith("\n") ? privateKey : privateKey + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        setPermissions(keyFile, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
        return keyFile;
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException exception) {
            // Windows 本地开发没有 POSIX 权限；Docker Runner 的 Linux 环境会生效。
            log.debug("Could not set POSIX permissions on {}", path);
        }
    }

    private void publishCloneOutput(Path cloneLog, Consumer<String> logConsumer) {
        if (logConsumer == null || cloneLog == null) {
            return;
        }
        String output = readCloneOutput(cloneLog);
        if (!output.isBlank()) {
            logConsumer.accept(SensitiveDataRedactor.redact(output));
        }
    }

    private String readCloneOutput(Path logFile) {
        try {
            long length = Files.size(logFile);
            if (length <= 0) {
                return "";
            }
            int readLength = (int) Math.min(length, MAX_CLONE_LOG_BYTES);
            byte[] bytes = new byte[readLength];
            try (var stream = Files.newInputStream(logFile)) {
                int offset = 0;
                while (offset < readLength) {
                    int read = stream.read(bytes, offset, readLength - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            return length > MAX_CLONE_LOG_BYTES ? text + "\n[clone log truncated...]" : text;
        } catch (IOException exception) {
            return "";
        }
    }

    private String normalizeAuthType(String authType) {
        String normalized = authType == null || authType.isBlank()
                ? AUTH_PUBLIC
                : authType.trim().toUpperCase(Locale.ROOT);
        if (!AUTH_PUBLIC.equals(normalized)
                && !AUTH_HTTPS_TOKEN.equals(normalized)
                && !AUTH_SSH_KEY.equals(normalized)) {
            throw new IllegalArgumentException("不支持的仓库认证方式: " + authType);
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时凭据文件清理失败不影响任务，但会记录到 server 日志。
            log.warn("Failed to delete temporary git credential file: {}", path);
        }
    }

    @Override
    public void cleanupWorkspace(Long taskId) {
        if (taskId == null) {
            return;
        }
        Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
        try {
            deleteWorkspace(taskWorkspace);
        } catch (IOException exception) {
            log.warn("Failed to cleanup runner workspace. taskId={}, reason={}", taskId, exception.getMessage());
        }
    }

    private void deleteWorkspace(Path taskWorkspace) throws IOException {
        if (!Files.exists(taskWorkspace)) {
            return;
        }
        try (var walk = Files.walk(taskWorkspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private String readTail(Path logFile) {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            int readLength = (int) Math.min(length, 4000);
            if (readLength <= 0) {
                return "";
            }
            file.seek(length - readLength);
            byte[] bytes = new byte[readLength];
            file.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return SensitiveDataRedactor.redact(text.length() <= 2000 ? text : text.substring(text.length() - 2000));
        } catch (IOException exception) {
            return "";
        }
    }
}
