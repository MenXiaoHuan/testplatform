package com.example.platform.evaluation.agent;

import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.target.EvalTargetEntity;
import com.example.platform.evaluation.target.EvalTargetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AgentController：外部 Agent 统一为 HTTP_AGENT，并校验在线 Trace 挂载。 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private EvalTargetMapper targetMapper;

    @Mock
    private EvalAgentConnectionMapper connectionMapper;

    private AgentController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentController(
                targetMapper, connectionMapper, new AgentTokenService(),
                "http://localhost:8080/api/evaluation/ingest");
    }

    @Test
    void shouldCreateTraceOnlyAgentAsHttpAgent() {
        when(targetMapper.countBySpaceAndName(1L, "trace-only")).thenReturn(0);
        when(targetMapper.insert(any(EvalTargetEntity.class))).thenAnswer(invocation -> {
            EvalTargetEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        AgentController.AgentCredentials credentials = controller.create(
                new AgentController.CreateAgentRequest(1L, "trace-only", null)).data();

        ArgumentCaptor<EvalTargetEntity> captor = ArgumentCaptor.forClass(EvalTargetEntity.class);
        verify(targetMapper).insert(captor.capture());
        assertEquals(TargetType.HTTP_AGENT.name(), captor.getValue().getTargetType());
        assertEquals(9L, credentials.targetId());
    }

    @Test
    void shouldAttachConnectionToExistingHttpAgent() {
        when(targetMapper.findById(5L)).thenReturn(Optional.of(target(5L, 1L, TargetType.HTTP_AGENT)));
        when(connectionMapper.findByTargetId(5L)).thenReturn(Optional.empty());

        AgentController.AgentCredentials credentials = controller.attach(5L, 1L).data();

        assertEquals(5L, credentials.targetId());
        assertNotNull(credentials.publicKey());
        assertNotNull(credentials.secretKey());
        assertEquals("http://localhost:8080/api/evaluation/ingest", credentials.ingestHost());
    }

    @Test
    void shouldRejectNonHttpAgent() {
        when(targetMapper.findById(5L)).thenReturn(Optional.of(target(5L, 1L, TargetType.LLM_API)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.attach(5L, 1L));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void shouldRejectDuplicateConnection() {
        when(targetMapper.findById(5L)).thenReturn(Optional.of(target(5L, 1L, TargetType.HTTP_AGENT)));
        when(connectionMapper.findByTargetId(5L))
                .thenReturn(Optional.of(new EvalAgentConnectionEntity()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.attach(5L, 1L));
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void shouldRejectCrossSpaceAccess() {
        when(targetMapper.findById(5L)).thenReturn(Optional.of(target(5L, 2L, TargetType.HTTP_AGENT)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.attach(5L, 1L));
        assertEquals("TARGET_NOT_FOUND", ex.getCode());
    }

    private static EvalTargetEntity target(Long id, Long spaceId, TargetType type) {
        EvalTargetEntity entity = new EvalTargetEntity();
        entity.setId(id);
        entity.setSpaceId(spaceId);
        entity.setName("agent-" + id);
        entity.setTargetType(type.name());
        entity.setStatus("ACTIVE");
        return entity;
    }
}
