package com.example.platform.evaluation.dataset;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.EvaluationErrorCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/** 评测集 REST Controller —— CRUD + 样本分页 + JSONL 导入。 */
@RestController
@RequestMapping("/api/evaluation/datasets")
public class DatasetController {

    private final EvalDatasetMapper datasetMapper;
    private final EvalDatasetSampleMapper sampleMapper;
    private final DatasetImportService importService;
    private final BuiltinDatasetService builtinDatasetService;
    private final BuiltinDatasetSyncService syncService;

    public DatasetController(EvalDatasetMapper datasetMapper,
                             EvalDatasetSampleMapper sampleMapper,
                             DatasetImportService importService,
                             BuiltinDatasetService builtinDatasetService,
                             BuiltinDatasetSyncService syncService) {
        this.datasetMapper = datasetMapper;
        this.sampleMapper = sampleMapper;
        this.importService = importService;
        this.builtinDatasetService = builtinDatasetService;
        this.syncService = syncService;
    }

    @PostMapping
    public ApiResponse<EvalDatasetEntity> create(@RequestBody EvalDatasetEntity entity) {
        if (entity.getSpaceId() == null) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "评测集名称不能为空");
        }
        if (datasetMapper.countBySpaceAndName(entity.getSpaceId(), entity.getName().trim()) > 0) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "同名评测集已存在");
        }
        entity.setName(entity.getName().trim());
        if (entity.getVersion() == null || entity.getVersion().isBlank()) entity.setVersion("v1");
        if (entity.getTotalSamples() == null) entity.setTotalSamples(0);
        if (entity.getAnnotatedSamples() == null) entity.setAnnotatedSamples(0);
        entity.setBuiltinKey(null);
        entity.setIsBuiltin(false);
        datasetMapper.insert(entity);
        return ApiResponse.ok(entity);
    }

    @GetMapping
    public ApiResponse<List<EvalDatasetEntity>> list(@RequestParam Long spaceId) {
        builtinDatasetService.ensureForSpace(spaceId);
        return ApiResponse.ok(datasetMapper.listBySpace(spaceId));
    }

    @GetMapping("/{id}")
    public ApiResponse<EvalDatasetEntity> get(@PathVariable Long id, @RequestParam Long spaceId) {
        return ApiResponse.ok(requireDataset(id, spaceId));
    }

    @GetMapping("/{id}/samples")
    public ApiResponse<SamplePage> samples(@PathVariable Long id,
                                           @RequestParam Long spaceId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        requireDataset(id, spaceId);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int offset = (safePage - 1) * safeSize;
        List<EvalDatasetSampleEntity> items = sampleMapper.listByDatasetPaged(id, offset, safeSize);
        int total = sampleMapper.countByDataset(id);
        return ApiResponse.ok(new SamplePage(items, total, safePage, safeSize));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long spaceId) {
        EvalDatasetEntity dataset = requireDataset(id, spaceId);
        if (Boolean.TRUE.equals(dataset.getIsBuiltin())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "内置评测集不可删除");
        }
        datasetMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/sync")
    public ApiResponse<EvalDatasetEntity> sync(@PathVariable Long id, @RequestParam Long spaceId) {
        EvalDatasetEntity dataset = requireDataset(id, spaceId);
        if (!Boolean.TRUE.equals(dataset.getIsBuiltin())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "仅内置评测集支持同步官方全集");
        }
        syncService.syncAsync(id);
        return ApiResponse.ok(datasetMapper.findById(id).orElse(dataset));
    }

    @PostMapping("/{id}/samples")
    public ApiResponse<EvalDatasetSampleEntity> createSample(@PathVariable Long id,
                                                             @RequestParam Long spaceId,
                                                             @RequestBody EvalDatasetSampleEntity body) {
        requireCustomDataset(id, spaceId);
        if (body.getInputText() == null || body.getInputText().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "用例输入不能为空");
        }
        body.setId(null);
        body.setDatasetId(id);
        body.setSampleIndex(sampleMapper.maxSampleIndex(id) + 1);
        sampleMapper.insert(body);
        refreshCounts(id);
        return ApiResponse.ok(sampleMapper.findById(body.getId()).orElse(body));
    }

    @PutMapping("/{id}/samples/{sampleId}")
    public ApiResponse<EvalDatasetSampleEntity> updateSample(@PathVariable Long id,
                                                             @PathVariable Long sampleId,
                                                             @RequestParam Long spaceId,
                                                             @RequestBody EvalDatasetSampleEntity body) {
        requireCustomDataset(id, spaceId);
        EvalDatasetSampleEntity sample = requireSample(id, sampleId);
        if (body.getInputText() == null || body.getInputText().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "用例输入不能为空");
        }
        sample.setInputText(body.getInputText());
        sample.setGoldenAnswer(body.getGoldenAnswer());
        sample.setKeywords(body.getKeywords());
        sample.setReferenceContext(body.getReferenceContext());
        sample.setMetadata(body.getMetadata());
        sample.setScenarioTags(body.getScenarioTags());
        sampleMapper.update(sample);
        return ApiResponse.ok(sampleMapper.findById(sampleId).orElse(sample));
    }

    @DeleteMapping("/{id}/samples/{sampleId}")
    public ApiResponse<Void> deleteSample(@PathVariable Long id,
                                          @PathVariable Long sampleId,
                                          @RequestParam Long spaceId) {
        requireCustomDataset(id, spaceId);
        requireSample(id, sampleId);
        sampleMapper.deleteById(sampleId);
        refreshCounts(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/import-jsonl")
    public ApiResponse<DatasetImportService.ImportResult> importJsonl(
            @PathVariable Long id, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long spaceId) throws Exception {
        EvalDatasetEntity dataset = datasetMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.BAD_REQUEST, "评测集 id=" + id + " 不存在"));
        if (spaceId != null && !dataset.getSpaceId().equals(spaceId)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "跨 Space 访问被拒绝");
        }
        if (Boolean.TRUE.equals(dataset.getIsBuiltin())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "内置评测集不支持导入");
        }
        try (InputStream is = file.getInputStream()) {
            return ApiResponse.ok(importService.importJsonl(id, is, file.getSize()));
        }
    }

    @PostMapping("/import-url")
    public ApiResponse<DatasetImportService.ImportResult> importUrl(
            @RequestBody java.util.Map<String, Object> body) {
        Long spaceId = body.get("spaceId") instanceof Number n ? n.longValue() : null;
        String url = body.get("url") instanceof String s ? s : null;
        if (spaceId == null || url == null || url.isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "spaceId / url 不能为空");
        }
        importService.validateUrlWhitelist(url);
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setSpaceId(spaceId);
        entity.setName(body.get("name") instanceof String name && !name.isBlank() ? name.trim() : "外部评测集");
        entity.setVersion("v1");
        entity.setExternalSource(url);
        entity.setIsBuiltin(false);
        entity.setTotalSamples(0);
        entity.setAnnotatedSamples(0);
        datasetMapper.insert(entity);
        return ApiResponse.ok(new DatasetImportService.ImportResult(0, 0, 0,
                List.of("URL 已通过白名单校验；自动下载与解析将在后续版本接入")));
    }

    private EvalDatasetEntity requireDataset(Long id, Long spaceId) {
        EvalDatasetEntity dataset = datasetMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.BAD_REQUEST, "评测集 id=" + id + " 不存在"));
        if (!dataset.getSpaceId().equals(spaceId)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "跨 Space 访问被拒绝");
        }
        return dataset;
    }

    private EvalDatasetEntity requireCustomDataset(Long id, Long spaceId) {
        EvalDatasetEntity dataset = requireDataset(id, spaceId);
        if (Boolean.TRUE.equals(dataset.getIsBuiltin())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "内置评测集不支持用例编辑");
        }
        return dataset;
    }

    private EvalDatasetSampleEntity requireSample(Long datasetId, Long sampleId) {
        EvalDatasetSampleEntity sample = sampleMapper.findById(sampleId)
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.BAD_REQUEST, "用例 id=" + sampleId + " 不存在"));
        if (!sample.getDatasetId().equals(datasetId)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "用例不属于当前评测集");
        }
        return sample;
    }

    private void refreshCounts(Long datasetId) {
        int count = sampleMapper.countByDataset(datasetId);
        datasetMapper.updateCounts(datasetId, count, count);
    }

    public record SamplePage(List<EvalDatasetSampleEntity> items, int total, int page, int size) {}
}
