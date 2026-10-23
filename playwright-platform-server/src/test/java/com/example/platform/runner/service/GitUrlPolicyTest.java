package com.example.platform.runner.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitUrlPolicyTest {
    @Test
    void shouldAcceptPublicHttpsAndSshUrls() {
        GitUrlPolicy.validate("https://github.com/demo/testframe.git");
        GitUrlPolicy.validate("ssh://git@github.com/demo/testframe.git");
        GitUrlPolicy.validate("git@github.com:demo/testframe.git");
    }

    @Test
    void shouldRejectLocalPrivateAndUnsupportedUrls() {
        assertThatThrownBy(() -> GitUrlPolicy.validate("http://github.com/demo/testframe.git"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("https://localhost/repo.git"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("https://127.0.0.1/repo.git"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("https://10.0.0.8/repo.git"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("-oProxyCommand=calc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validate("https://token@github.com/demo/testframe.git"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldValidateBranchNames() {
        assertThat(GitUrlPolicy.validateBranch("main")).isEqualTo("main");
        assertThat(GitUrlPolicy.validateBranch("release/1.0")).isEqualTo("release/1.0");
        assertThatThrownBy(() -> GitUrlPolicy.validateBranch("-upload-pack=calc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitUrlPolicy.validateBranch("main..backup"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
