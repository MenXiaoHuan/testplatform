package com.example.platform.evaluation.prompt;

import com.example.platform.evaluation.common.PromptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PromptVersionService：标识生成、状态流转与任意版本删除。 */
@ExtendWith(MockitoExtension.class)
class PromptVersionServiceTest {

    @Mock
    private EvalPromptVersionMapper mapper;

    @Mock
    private PromptCache cache;

    private PromptVersionService service;

    @BeforeEach
    void setUp() {
        service = new PromptVersionService(mapper, cache);
    }

    @Test
    void shouldGeneratePromptKeyWhenCreateWithoutKey() {
        EvalPromptVersionEntity entity = new EvalPromptVersionEntity();
        entity.setSpaceId(1L);
        entity.setName("客服提示词");
        entity.setVersionLabel("v1");
        entity.setPromptType("SYSTEM");
        entity.setTemplate("[{\"role\":\"system\",\"content\":\"hi\"}]");
        entity.setVariables("[]");

        when(mapper.countByPromptKey(eq(1L), anyString())).thenReturn(0);
        when(mapper.findByPromptKeyAndVersion(eq(1L), anyString(), eq("v1")))
                .thenReturn(Optional.empty());
        when(mapper.insert(any(EvalPromptVersionEntity.class))).thenAnswer(invocation -> {
            EvalPromptVersionEntity saved = invocation.getArgument(0);
            saved.setId(10L);
            return 1;
        });
        when(mapper.findById(10L)).thenReturn(Optional.of(entity));

        EvalPromptVersionEntity created = service.create(entity);

        assertTrue(created.getPromptKey().matches("^prompt-[0-9a-f]{12}$"));
    }

    @Test
    void shouldTransitionDeprecatedVersionToActiveAndDeprecateOldActive() {
        EvalPromptVersionEntity target = version(5L, "v1", PromptStatus.DEPRECATED);
        EvalPromptVersionEntity oldActive = version(6L, "v2", PromptStatus.ACTIVE);
        when(mapper.findById(5L)).thenReturn(Optional.of(target));
        when(mapper.findByPromptKeyAndStatus(1L, "prompt-x", PromptStatus.ACTIVE.name()))
                .thenReturn(Optional.of(oldActive));

        service.changeStatus(5L, 1L, PromptStatus.ACTIVE.name());

        verify(mapper).updateStatus(6L, PromptStatus.DEPRECATED.name(), 5L);
        verify(mapper).updateStatus(eq(5L), eq(PromptStatus.ACTIVE.name()), isNull());
        verify(cache).invalidate(1L, "prompt-x");
    }

    @Test
    void shouldDeleteVersionRegardlessOfStatus() {
        EvalPromptVersionEntity target = version(5L, "v1", PromptStatus.ACTIVE);
        when(mapper.findById(5L)).thenReturn(Optional.of(target));

        service.deleteVersion(5L, 1L);

        verify(mapper).deleteById(5L);
        verify(cache).invalidate(1L, "prompt-x");
    }

    @Test
    void shouldDeleteWholePromptGroup() {
        when(mapper.countByPromptKey(1L, "prompt-x")).thenReturn(2);

        service.deletePromptGroup(1L, "prompt-x");

        verify(mapper).deleteByPromptKey(1L, "prompt-x");
        verify(cache).invalidate(1L, "prompt-x");
    }

    private static EvalPromptVersionEntity version(Long id, String versionLabel, PromptStatus status) {
        EvalPromptVersionEntity entity = new EvalPromptVersionEntity();
        entity.setId(id);
        entity.setSpaceId(1L);
        entity.setPromptKey("prompt-x");
        entity.setVersionLabel(versionLabel);
        entity.setStatus(status.name());
        return entity;
    }
}
