package com.example.platform.auth.service;

import com.example.platform.auth.mapper.UserSessionMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 清理过期登录会话。
 *
 * <p>登录会话只在被访问时删除过期行；如果用户不再回来，表会慢慢累积。
 * 个人项目不需要复杂的会话治理，一条按天执行的 DELETE 就够了。
 */
@Service
public class UserSessionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(UserSessionCleanupService.class);

    private final UserSessionMapper userSessionMapper;

    public UserSessionCleanupService(UserSessionMapper userSessionMapper) {
        this.userSessionMapper = userSessionMapper;
    }

    @Scheduled(cron = "${platform.auth.session-cleanup-cron:0 15 4 * * *}")
    public void cleanupExpiredSessions() {
        int deleted = userSessionMapper.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Expired user sessions cleaned. deleted={}", deleted);
        }
    }
}
