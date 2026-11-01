package com.example.platform.auth.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.auth")
public class AuthProperties {
    private String cookieName = "platform_session";
    private int slidingDays = 14;
    /**
     * 注册开关的默认值。运行时以数据库 platform_setting 表为准（超管在用户管理页维护），
     * 该配置只在数据库尚未写入时作为首次启动的兜底。
     */
    private boolean registrationEnabled = false;
    private List<UserConfig> users = new ArrayList<>();

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public int getSlidingDays() {
        return slidingDays;
    }

    public void setSlidingDays(int slidingDays) {
        this.slidingDays = slidingDays;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public List<UserConfig> getUsers() {
        return users;
    }

    public void setUsers(List<UserConfig> users) {
        this.users = users;
    }

    public static class UserConfig {
        private Long id;
        private String username;
        private String nickname;
        private String passwordHash;
        private String avatarObjectKey;
        private boolean enabled = true;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public String getAvatarObjectKey() {
            return avatarObjectKey;
        }

        public void setAvatarObjectKey(String avatarObjectKey) {
            this.avatarObjectKey = avatarObjectKey;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
