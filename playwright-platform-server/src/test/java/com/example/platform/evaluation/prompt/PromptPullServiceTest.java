package com.example.platform.evaluation.prompt;

import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.exception.PromptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PromptPullService 单测 —— 外部 Pull API 的 ETag 协商缓存行为。
 */
@ExtendWith(MockitoExtension.class)
class PromptPullServiceTest {

    @Mock
    private EvalPromptVersionMapper mapper;

    @Mock
    private PromptCache cache;

    private PromptPullService service;

    @BeforeEach
    void setUp() {
        service = new PromptPullService(mapper, cache);
    }

    private static EvalPromptVersionEntity activeVersion() {
        EvalPromptVersionEntity entity = new EvalPromptVersionEntity();
        entity.setId(2L);
        entity.setSpaceId(1L);
        entity.setPromptKey("demo-greeting");
        entity.setVersionLabel("v1");
        entity.setStatus(PromptStatus.ACTIVE.name());
        entity.setTemplate("[{\"role\":\"system\",\"content\":\"hi\"}]");
        entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return entity;
    }

    @Test
    void shouldReturnNotModifiedWhenEtagMatchesEvenWithHttpQuotes() {
        when(cache.getActive(eq(1L), eq("demo-greeting"), any())).thenReturn(activeVersion());

        PromptPullService.PromptPullResult first = service.pullActive("demo-greeting", 1L, null);
        assertTrue(first.isOk());

        // HTTP 头里的 ETag 带双引号，服务端应去掉引号后比较
        PromptPullService.PromptPullResult second =
                service.pullActive("demo-greeting", 1L, "\"" + first.etag() + "\"");

        assertFalse(second.isOk(), "quoted ETag should yield NOT_MODIFIED");
        assertEquals(first.etag(), second.etag());
    }

    @Test
    void shouldReturnOkWhenEtagDiffers() {
        when(cache.getActive(eq(1L), eq("demo-greeting"), any())).thenReturn(activeVersion());

        PromptPullService.PromptPullResult result = service.pullActive("demo-greeting", 1L, "W/old:hash:0");

        assertTrue(result.isOk());
        assertEquals("v1", result.version().getVersionLabel());
    }

    @Test
    void shouldThrowNotFoundWhenNoActiveVersion() {
        when(cache.getActive(eq(1L), eq("missing"), any())).thenReturn(null);

        assertThrows(PromptNotFoundException.class,
                () -> service.pullActive("missing", 1L, null));
    }
}
