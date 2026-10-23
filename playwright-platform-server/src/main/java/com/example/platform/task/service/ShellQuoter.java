package com.example.platform.task.service;

/**
 * 将用户配置的测试目标/项目名安全地拼进执行命令。
 *
 * <p>Runner 在 Linux 容器中通过 {@code sh -c} 执行命令，因此这里使用 POSIX
 * 单引号转义：值内部的单引号写成 {@code '\''}。不要把该结果再包一层双引号。
 */
public final class ShellQuoter {
    private ShellQuoter() {
    }

    public static String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
