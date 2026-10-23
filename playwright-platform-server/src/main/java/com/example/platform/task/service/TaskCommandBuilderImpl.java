package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 任务命令构建器实现 —— 根据代码仓库配置和场景配置构建完整的 Playwright 测试执行命令。
 *
 * <p>命令规则（按优先级）：
 * <ol>
 *   <li>模板包含 {@code {target}} / {@code {project}} / {@code {testRoot}} 占位符时，
 *       只替换占位符，不再自动追加任何参数；</li>
 *   <li>没有占位符且命令是 Playwright CLI（npx/pnpm/yarn/npm exec）时，
 *       自动追加测试目标和 {@code --project}；</li>
 *   <li>其他命令视为“完全接管”，原样执行，避免注入 Playwright 不认识的
 *       {@code --target} 参数。</li>
 * </ol>
 *
 * <p>所有替换进命令的路径和项目名都经过 {@link ShellQuoter} 做 POSIX 单引号转义。
 */
@Service
public class TaskCommandBuilderImpl implements TaskCommandBuilder {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{(target|project|testRoot)}");
    private static final Pattern PLAYWRIGHT_CLI = Pattern.compile(
            "^(?:npx|pnpm|yarn|npm)\\s+(?:exec\\s+)?playwright\\s+test\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_OPTION = Pattern.compile(
            "(?is).*--project(?:=|\\s+).*");

    @Override
    public String buildRunCommand(TestRepositoryEntity repository, SceneEntity scene) {
        String baseCommand = repository.getRunCommandTemplate();
        if (baseCommand == null || baseCommand.isBlank()) {
            baseCommand = scene.getRunCommand();
        }
        if (baseCommand == null) {
            baseCommand = "";
        }
        String template = baseCommand.trim();
        String testRoot = normalizeTestRoot(repository.getTestRoot());
        String target = resolveTarget(testRoot, scene.getMatchValue());
        String project = scene.getBrowser() == null ? "" : scene.getBrowser().trim();

        if (PLACEHOLDER.matcher(template).find()) {
            return renderTemplate(template, target, project, testRoot).trim();
        }

        if (PLAYWRIGHT_CLI.matcher(template).matches()) {
            StringBuilder builder = new StringBuilder(template);
            if (!target.isBlank()) {
                builder.append(' ').append(ShellQuoter.quote(target));
            }
            if (!project.isBlank() && !PROJECT_OPTION.matcher(template).matches()) {
                builder.append(" --project ").append(ShellQuoter.quote(project));
            }
            return builder.toString();
        }

        // 自定义命令（npm script、pytest、私有 runner 等）自行处理参数；
        // 平台不再追加 --target，避免生成非法命令。
        return template;
    }

    private String renderTemplate(String template, String target, String project, String testRoot) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = switch (matcher.group(1)) {
                case "target" -> target;
                case "project" -> project;
                case "testRoot" -> testRoot;
                default -> "";
            };
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(value.isBlank() ? "" : ShellQuoter.quote(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String resolveTarget(String testRoot, String matchValue) {
        if (matchValue == null || matchValue.isBlank()) {
            return "";
        }
        String normalizedMatch = matchValue.replaceAll("^/+", "");
        return testRoot.isBlank() ? normalizedMatch : testRoot + "/" + normalizedMatch;
    }

    private String normalizeTestRoot(String testRoot) {
        if (testRoot == null || testRoot.isBlank()) {
            return "";
        }
        return testRoot.replaceAll("/+$", "");
    }
}
