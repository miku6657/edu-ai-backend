package com.edu.ai.task.service;

import com.edu.ai.task.dto.DraftDecisionRequest;
import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.entity.TaskChangeDraft;
import com.edu.ai.task.mapper.DdlTaskMapper;
import com.edu.ai.task.mapper.TaskChangeDraftMapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class TaskChangeDraftServiceTest {

    private DdlTaskMapper taskMapper;
    private TaskChangeDraftMapper draftMapper;
    private TaskChangeDraftService service;

    private final DdlTaskStatusResolver statusResolver =
            new DdlTaskStatusResolver();

    private final DdlDisplayCodeGenerator displayCodeGenerator =
            new DdlDisplayCodeGenerator();

    @BeforeAll
    static void initMybatisTableInfo() {
        MybatisConfiguration configuration =
                new MybatisConfiguration();

        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(configuration, "");

        TableInfoHelper.initTableInfo(
                assistant,
                TaskChangeDraft.class
        );

        TableInfoHelper.initTableInfo(
                assistant,
                DdlTask.class
        );
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(DdlTaskMapper.class);
        draftMapper = mock(TaskChangeDraftMapper.class);
        service = new TaskChangeDraftService(
                taskMapper,
                draftMapper,
                displayCodeGenerator,
                statusResolver
        );
    }

    private DdlTask originalTask(Long id, Long userId) {
        DdlTask task = new DdlTask();
        task.setId(id);
        task.setUserId(userId);
        task.setTaskTitle("旧标题");
        task.setDescription("旧描述");
        task.setTimePrecision("EXACT_DATETIME");
        task.setDueDate(LocalDate.of(2026, 11, 20));
        task.setDueTime(LocalTime.of(23, 59));
        task.setDueAt(LocalDateTime.of(2026, 11, 20, 23, 59));
        task.setRelativeTime(false);
        task.setSourcePlatform("MANUAL");
        return task;
    }

    private TaskChangeDraft pendingDraft(Long targetTaskId, Long userId) {
        TaskChangeDraft draft = new TaskChangeDraft();
        draft.setId(10L);
        draft.setTargetTaskId(targetTaskId);
        draft.setUserId(userId);
        draft.setNewTaskTitle("新标题");
        draft.setNewDescription("新描述");
        draft.setNewTimePrecision("EXACT_DATETIME");
        draft.setNewDueDate(LocalDate.of(2026, 11, 25));
        draft.setNewDueTime(LocalTime.of(23, 59));
        draft.setNewDueAt(LocalDateTime.of(2026, 11, 25, 23, 59));
        draft.setNewTimeInferred(false);
        draft.setNewRelativeTime(true);
        draft.setNewConfidence(new BigDecimal("0.95"));
        draft.setStatus("PENDING");
        draft.setExpiresAt(LocalDateTime.now().plusHours(1));
        return draft;
    }

    private DraftDecisionRequest decision(
            DraftDecisionRequest.Decision d, String requestId) {
        DraftDecisionRequest request = new DraftDecisionRequest();
        request.setDecision(d);
        request.setClientRequestId(requestId);
        return request;
    }

    @Test
    void confirmUpdateShouldUpdateOriginalTaskInPlace() {
        Long taskId = 100L;
        Long userId = 1L;

        DdlTask original = originalTask(taskId, userId);
        TaskChangeDraft draft = pendingDraft(taskId, userId);

        when(draftMapper.selectOne(any()))
                .thenReturn(null, draft);
        when(taskMapper.selectOne(any())).thenReturn(original);
        when(draftMapper.update(isNull(), any())).thenReturn(1);

        DdlTask result = service.decide(
                taskId, userId,
                decision(
                        DraftDecisionRequest.Decision.CONFIRM_UPDATE,
                        "req-1"
                )
        );

        assertSame(original, result);
        assertEquals(taskId, result.getId());
        assertEquals("新标题", result.getTaskTitle());
        assertEquals("新描述", result.getDescription());
        assertEquals(true, result.getRelativeTime());
        assertEquals(new BigDecimal("0.95"), result.getConfidence());
        verify(taskMapper).updateById(original);
        verify(taskMapper, never()).insert(any(DdlTask.class));
    }

    @Test
    void createNewShouldCreateTaskAndKeepOriginal() {
        Long taskId = 100L;
        Long userId = 1L;

        DdlTask original = originalTask(taskId, userId);
        TaskChangeDraft draft = pendingDraft(taskId, userId);

        when(draftMapper.selectOne(any()))
                .thenReturn(null, draft);
        when(taskMapper.selectOne(any())).thenReturn(original);
        when(draftMapper.update(isNull(), any())).thenReturn(1);
        when(taskMapper.insert(any(DdlTask.class))).thenReturn(1);

        DdlTask result = service.decide(
                taskId, userId,
                decision(
                        DraftDecisionRequest.Decision.CREATE_NEW,
                        "req-2"
                )
        );

        assertNotSame(original, result);
        assertEquals("新标题", result.getTaskTitle());
        assertEquals("新描述", result.getDescription());
        assertEquals(true, result.getRelativeTime());
        assertEquals(new BigDecimal("0.95"), result.getConfidence());
        assertEquals("AI", result.getSourcePlatform());

        // 原任务保持不变
        assertEquals("旧标题", original.getTaskTitle());
        assertEquals("旧描述", original.getDescription());
        verify(taskMapper).insert(any(DdlTask.class));
    }

    @Test
    void discardShouldNotModifyOrCreateTask() {
        Long taskId = 100L;
        Long userId = 1L;

        DdlTask original = originalTask(taskId, userId);
        TaskChangeDraft draft = pendingDraft(taskId, userId);

        when(draftMapper.selectOne(any()))
                .thenReturn(null, draft);
        when(taskMapper.selectOne(any())).thenReturn(original);
        when(draftMapper.update(isNull(), any())).thenReturn(1);

        DdlTask result = service.decide(
                taskId, userId,
                decision(
                        DraftDecisionRequest.Decision.DISCARD,
                        "req-3"
                )
        );

        assertSame(original, result);
        assertEquals("旧标题", original.getTaskTitle());
        assertEquals("DISCARDED", draft.getStatus());
        verify(taskMapper, never()).updateById(any(DdlTask.class));
        verify(taskMapper, never()).insert(any(DdlTask.class));
    }

    @Test
    void repeatedRequestShouldReturnPreviousResult() {
        Long taskId = 100L;
        Long userId = 1L;

        DdlTask original = originalTask(taskId, userId);

        TaskChangeDraft previous = new TaskChangeDraft();
        previous.setTargetTaskId(taskId);
        previous.setUserId(userId);
        previous.setDecision("CONFIRM_UPDATE");
        previous.setDecisionRequestId("req-1");
        previous.setResultTaskId(taskId);
        previous.setStatus("CONFIRMED");

        when(draftMapper.selectOne(any())).thenReturn(previous);
        when(taskMapper.selectOne(any())).thenReturn(original);
        when(taskMapper.selectById(taskId)).thenReturn(original);

        DdlTask result = service.decide(
                taskId, userId,
                decision(
                        DraftDecisionRequest.Decision.CONFIRM_UPDATE,
                        "req-1"
                )
        );

        assertSame(original, result);
        verify(taskMapper, never()).insert(any(DdlTask.class));
    }

    @Test
    void expiredDraftShouldBeRejected() {
        Long taskId = 100L;
        Long userId = 1L;

        DdlTask original = originalTask(taskId, userId);
        TaskChangeDraft draft = pendingDraft(taskId, userId);
        draft.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(draftMapper.selectOne(any()))
                .thenReturn(null, draft);
        when(taskMapper.selectOne(any())).thenReturn(original);

        assertThrows(
                com.edu.ai.common.BusinessException.class,
                () -> service.decide(
                        taskId, userId,
                        decision(
                                DraftDecisionRequest.Decision.CONFIRM_UPDATE,
                                "req-4"
                        )
                )
        );
    }

    @Test
    void draftOfOtherUserShouldBeRejected() {
        Long taskId = 100L;
        Long userId = 1L;

        when(taskMapper.selectOne(any())).thenReturn(null);

        assertThrows(
                com.edu.ai.common.BusinessException.class,
                () -> service.decide(
                        taskId, userId,
                        decision(
                                DraftDecisionRequest.Decision.CONFIRM_UPDATE,
                                "req-5"
                        )
                )
        );
    }
}
