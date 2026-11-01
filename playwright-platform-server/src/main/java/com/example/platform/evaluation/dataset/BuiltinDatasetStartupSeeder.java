package com.example.platform.evaluation.dataset;

import com.example.platform.space.mapper.SpaceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后为已有 Space 预置内置评测集，并自动触发官方全集同步；
 * 新建 Space 在首次打开评测集列表时由 {@link BuiltinDatasetService#ensureForSpace} 自动补齐并同步。
 */
@Component
public class BuiltinDatasetStartupSeeder {

    private static final Logger log = LoggerFactory.getLogger(BuiltinDatasetStartupSeeder.class);

    private final SpaceMapper spaceMapper;
    private final BuiltinDatasetService builtinDatasetService;

    public BuiltinDatasetStartupSeeder(SpaceMapper spaceMapper,
                                       BuiltinDatasetService builtinDatasetService) {
        this.spaceMapper = spaceMapper;
        this.builtinDatasetService = builtinDatasetService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedExistingSpaces() {
        spaceMapper.findAll().forEach(space -> {
            try {
                builtinDatasetService.ensureForSpace(space.getId());
            } catch (Exception e) {
                log.warn("Seed builtin datasets for space {} failed: {}", space.getId(), e.getMessage());
            }
        });
    }
}
