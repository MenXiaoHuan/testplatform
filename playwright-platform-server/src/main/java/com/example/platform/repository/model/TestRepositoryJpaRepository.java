package com.example.platform.repository.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepositoryJpaRepository extends JpaRepository<TestRepositoryEntity, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
