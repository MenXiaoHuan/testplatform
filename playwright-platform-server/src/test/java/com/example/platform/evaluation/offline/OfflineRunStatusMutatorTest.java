package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.common.EvalRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * OfflineRunStatusMutator 单测 —— 验证：
 * 1) cancel() 调用 5 参数 forceStatusUpdate（P0 编译修复点）
 * 2) transition() 乐观锁成功/失败路径
 */
@ExtendWith(MockitoExtension.class)
class OfflineRunStatusMutatorTest {

    @Mock
    private EvalRunMapper mapper;

    private OfflineRunStatusMutator mutator;

    @BeforeEach
    void setUp() {
        mutator = new OfflineRunStatusMutator(mapper);
    }

    // ========== cancel() 修复验证 ==========

    @Test
    void cancelShouldCallFiveParamForceStatusUpdate() {
        // 这就是修复前会编译失败的调用签名
        mutator.cancel(42L);

        verify(mapper, times(1)).forceStatusUpdate(
                eq(42L),
                eq(EvalRunStatus.CANCELLED.name()),
                eq(0),
                eq(0),
                isNull()
        );
    }

    // ========== transition() 成功路径 ==========

    @Test
    void transitionShouldSucceedWhenStatusAndVersionMatch() {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(1L);
        entity.setStatus("PENDING");
        entity.setVersion(3L);

        when(mapper.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.updateStatusAndVersion(eq(1L), eq("PENDING"), eq("RUNNING"), eq(3L), anyString()))
                .thenReturn(1);

        boolean ok = mutator.transition(1L, "PENDING", "RUNNING");

        assertThat(ok).isTrue();
        verify(mapper).updateStatusAndVersion(eq(1L), eq("PENDING"), eq("RUNNING"), eq(3L),
                eq("started_at = current_timestamp,"));
    }

    // ========== transition() version 冲突 ==========

    @Test
    void transitionShouldFailWhenMapperReturnsZero() {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(1L);
        entity.setStatus("RUNNING"); // 实际状态已变
        entity.setVersion(4L);

        when(mapper.findById(1L)).thenReturn(Optional.of(entity));
        // 期望 fromStatus=PENDING，但实际已经是 RUNNING
        boolean ok = mutator.transition(1L, "PENDING", "COMPLETED");

        assertThat(ok).isFalse();
        verify(mapper, never()).updateStatusAndVersion(any(), any(), any(), any(), anyString());
    }

    // ========== transition() Run 不存在 ==========

    @Test
    void transitionShouldFailWhenRunNotFound() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        boolean ok = mutator.transition(999L, "PENDING", "RUNNING");
        assertThat(ok).isFalse();
    }

    // ========== transitionWithRetry 重试 ==========

    @Test
    void transitionWithRetryShouldEventuallySucceed() {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(1L);
        entity.setStatus("PENDING");
        entity.setVersion(1L);

        when(mapper.findById(1L)).thenReturn(Optional.of(entity));
        // 前两次冲突，第三次成功
        when(mapper.updateStatusAndVersion(eq(1L), eq("PENDING"), eq("RUNNING"), eq(1L), anyString()))
                .thenReturn(0, 0, 1);

        boolean ok = mutator.transitionWithRetry(1L, "PENDING", "RUNNING");
        assertThat(ok).isTrue();
        verify(mapper, times(3)).updateStatusAndVersion(any(), any(), any(), any(), anyString());
    }
}
