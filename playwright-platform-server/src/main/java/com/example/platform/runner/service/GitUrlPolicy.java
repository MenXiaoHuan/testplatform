package com.example.platform.runner.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 地址安全策略。
 *
 * <p>平台会让服务端主动执行 {@code git clone}，所以仓库地址不是普通文本：
 * <ul>
 *   <li>只允许 https / ssh，避免 file://、ext:: 等本地传输；</li>
 *   <li>拒绝回环/内网/链路本地地址，降低 SSRF 风险；</li>
 *   <li>拒绝以 "-" 开头的地址，避免被 git 当作命令行选项；</li>
 *   <li>分支名单独校验，避免把选项注入到 clone 命令。</li>
 * </ul>
 *
 * <p>这不是完整的网络沙箱，但能挡住最常见的误用和低成本攻击。
 */
public final class GitUrlPolicy {
    private static final Pattern SCP_LIKE = Pattern.compile("^[\\w.-]+@([\\w.-]+):[^\\s]+$");
    private static final Pattern BRANCH = Pattern.compile("^[A-Za-z0-9._/-]{1,128}$");

    private GitUrlPolicy() {
    }

    public static void validate(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("Git 仓库地址不能为空");
        }
        String value = gitUrl.trim();
        if (value.startsWith("-")) {
            throw new IllegalArgumentException("Git 仓库地址不能以 - 开头");
        }

        Matcher scpMatcher = SCP_LIKE.matcher(value);
        if (scpMatcher.matches()) {
            validateHost(scpMatcher.group(1));
            return;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Git 仓库地址格式不合法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("ssh")) {
            throw new IllegalArgumentException("Git 仓库地址只支持 https:// 或 ssh://");
        }
        if (scheme.equals("https") && uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Git 仓库地址不能包含用户名或 token；请使用服务器侧 SSH key 或 HTTPS credential 文件");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Git 仓库地址缺少主机名");
        }
        validateHost(uri.getHost());
    }

    public static String validateBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("分支不能为空");
        }
        String value = branch.trim();
        if (!BRANCH.matcher(value).matches()) {
            throw new IllegalArgumentException("分支名不合法: " + value);
        }
        if (value.startsWith("-") || value.contains("..") || value.endsWith("/") || value.endsWith(".lock")) {
            throw new IllegalArgumentException("分支名不合法: " + value);
        }
        return value;
    }

    private static void validateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            throw new IllegalArgumentException("Git 仓库地址不能指向本机: " + host);
        }
        // 只检查 IP 字面量，不做 DNS 解析：避免保存仓库时依赖网络，
        // 也避免单元测试因为 DNS 不可用而失败。DNS rebinding 不在这个规模的处理范围内。
        String ipv4 = normalized.startsWith("[") ? normalized.substring(1, normalized.length() - 1) : normalized;
        if (ipv4.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            String[] parts = ipv4.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            boolean privateIpv4 = first == 10
                    || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 100 && second >= 64 && second <= 127)
                    || first == 0;
            if (privateIpv4) {
                throw new IllegalArgumentException("Git 仓库地址不能指向内网或本机地址: " + host);
            }
        }
        if (ipv4.equals("::1") || ipv4.startsWith("fc") || ipv4.startsWith("fd")
                || ipv4.startsWith("fe80")) {
            throw new IllegalArgumentException("Git 仓库地址不能指向内网或本机地址: " + host);
        }
    }

}
