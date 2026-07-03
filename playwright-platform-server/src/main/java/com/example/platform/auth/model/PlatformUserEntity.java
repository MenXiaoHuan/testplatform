package com.example.platform.auth.model;

import java.time.LocalDateTime;

public class PlatformUserEntity {
    private Long id;
    private String username;
    private String nickname;
    private String passwordHash;
    private String avatarObjectKey;
    private Boolean enabled;
    private Long lastSpaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAvatarObjectKey() { return avatarObjectKey; }
    public void setAvatarObjectKey(String avatarObjectKey) { this.avatarObjectKey = avatarObjectKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getLastSpaceId() { return lastSpaceId; }
    public void setLastSpaceId(Long lastSpaceId) { this.lastSpaceId = lastSpaceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
