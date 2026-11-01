package com.example.platform.evaluation.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨同步一次内置评测集官方全集。 */
@Component
public class BuiltinDatasetSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BuiltinDatasetSyncScheduler.class);

    private final EvalDatasetMapper datasetMapper;
    private final BuiltinDatasetSyncService syncService;

    public BuiltinDatasetSyncScheduler(EvalDatasetMapper datasetMapper,
                                       BuiltinDatasetSyncService syncService) {
        this.datasetMapper = datasetMapper;
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void syncDaily() {
        for (EvalDatasetEntity dataset : datasetMapper.listBuiltin()) {
            try {
                syncService.syncAsync(dataset.getId());
            } catch (Exception e) {
                log.warn("Schedule builtin dataset sync failed: dataset={}, error={}",
                        dataset.getId(), e.getMessage());
            }
        }
    }
}
