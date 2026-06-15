package com.example.platform.audit.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogJpaRepository extends JpaRepository<PlatformAuditLogEntity, Long> {
}
