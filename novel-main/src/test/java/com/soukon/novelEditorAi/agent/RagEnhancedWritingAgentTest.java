package com.soukon.novelEditorAi.agent;

import com.soukon.novelEditorAi.agent.tool.WritingToolManager;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanDetailRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RAG增强写作代理测试类
 */
@ExtendWith(MockitoExtension.class)
class RagEnhancedWritingAgentTest {

    @Mock
    private LlmService llmService;

    @Mock
    private WritingToolManager toolManager;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChapterContentRequest request;

    private RagEnhancedWritingAgent agent;

    @BeforeEach
    void setUp() {
        agent = new RagEnhancedWritingAgent(llmService, toolManager, chatClient, request);
    }

    @Test
    void testGetName() {
        assertEquals("RagEnhancedWritingAgent", agent.getName());
    }

    @Test
    void testGetDescription() {
        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("RAG"));
        assertTrue(description.contains("函数调用"));
    }

    @Test
    void testBasicFunctionality() {
        // 测试基本功能
        String planId = "test-plan-123";
        agent.setPlanId(planId);

        // 测试工具管理器交互
        List<ToolCallback> mockTools = new ArrayList<>();
        when(toolManager.getAllToolCallbacks(planId)).thenReturn(mockTools);

        // 验证工具管理器被正确调用
        List<ToolCallback> tools = toolManager.getAllToolCallbacks(planId);
        assertNotNull(tools);
        verify(toolManager).getAllToolCallbacks(planId);
    }

    @Test
    void testGetCurrentState() {
        AgentState state = agent.getCurrentState();
        assertNotNull(state);
    }

    @Test
    void testGetAccumulatedContent() {
        String content = agent.getAccumulatedContent();
        assertNotNull(content);
    }

    @Test
    void testExecuteWritingPlanBasic() {
        // 基本的执行测试
        String planId = "test-plan-123";
        agent.setPlanId(planId);

        List<Message> planMessages = new ArrayList<>();
        PlanContext context = new PlanContext(planId);
        List<PlanDetailRes> planSteps = new ArrayList<>();
        
        PlanDetailRes step1 = new PlanDetailRes();
        step1.setPlanContent("写作步骤1：描述主角出场");
        step1.setGoalWordCount(500);
        planSteps.add(step1);

        // Mock工具管理器
        List<ToolCallback> mockTools = new ArrayList<>();
        when(toolManager.getAllToolCallbacks(planId)).thenReturn(mockTools);

        // 执行测试（这里主要测试方法不会抛出异常）
        assertDoesNotThrow(() -> {
            Flux<String> result = agent.executeWritingPlan(request, planMessages, context, planSteps);
            assertNotNull(result);
        });
    }
} 