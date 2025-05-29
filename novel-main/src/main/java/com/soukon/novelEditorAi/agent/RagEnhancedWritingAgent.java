package com.soukon.novelEditorAi.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.soukon.novelEditorAi.agent.tool.WritingToolManager;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanDetailRes;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 集成RAG和函数调用的增强写作代理
 * 基于manus示例和template的RAG实现
 */
@Slf4j
public class RagEnhancedWritingAgent extends BaseAgent {
    
    private final WritingToolManager toolManager;
    private final ChatClient chatClient;
    private PlanContext context;
    private List<PlanDetailRes> planSteps;
    private int currentStepIndex = 0;
    private StringBuilder accumulatedContent = new StringBuilder();
    
    public RagEnhancedWritingAgent(LlmService llmService, 
                                  WritingToolManager toolManager,
                                  ChatClient chatClient,
                                  ChapterContentRequest request) {
        super(llmService, request);
        this.toolManager = toolManager;
        this.chatClient = chatClient;
    }
    
    @Override
    public String getName() {
        return "RagEnhancedWritingAgent";
    }
    
    @Override
    public String getDescription() {
        return "集成RAG搜索和函数调用功能的增强写作代理，能够查询相关信息并创作高质量小说内容";
    }
    
    @Override
    protected Message getNextStepWithEnvMessage() {
        if (planSteps != null && currentStepIndex < planSteps.size()) {
            PlanDetailRes currentStep = planSteps.get(currentStepIndex);
            String message = String.format("执行写作步骤 %d/%d: %s", 
                    currentStepIndex + 1, planSteps.size(), 
                    currentStep.getPlanContent() != null ? currentStep.getPlanContent() : "写作任务");
            return new UserMessage(message);
        }
        return new UserMessage("继续执行写作任务");
    }
    
    @Override
    protected AgentExecResult step() {
        try {
            if (planSteps == null || currentStepIndex >= planSteps.size()) {
                return new AgentExecResult("所有写作步骤已完成", AgentState.COMPLETED);
            }
            
            PlanDetailRes currentStep = planSteps.get(currentStepIndex);
            log.info("[RAG增强写作] 执行步骤 {}: {}", currentStepIndex + 1, 
                    currentStep.getPlanContent() != null ? currentStep.getPlanContent() : "写作任务");
            
            // 获取所有可用的工具
            toolManager.initializeForPlan(planId);
            List<Object> tools = toolManager.getAllTools();
            
            // 构建增强的系统提示词
            String enhancedSystemPrompt = buildEnhancedSystemPrompt();
            
            // 创建带工具的ChatClient
            ChatClient toolEnabledClient = chatClient.mutate()
                    .defaultTools(tools.toArray())
                    .build();
            
            // 构建任务描述
            String taskDescription = buildTaskDescription(currentStep);
            
            // 执行写作
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(enhancedSystemPrompt));
            messages.add(new UserMessage(taskDescription));
            
            String stepContent = toolEnabledClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
            
            // 累积内容
            accumulatedContent.append(stepContent);
            
            // 移动到下一步
            currentStepIndex++;
            
            if (currentStepIndex >= planSteps.size()) {
                return new AgentExecResult("写作完成: " + stepContent, AgentState.COMPLETED);
            } else {
                return new AgentExecResult("步骤完成: " + stepContent, AgentState.IN_PROGRESS);
            }
            
        } catch (Exception e) {
            log.error("[RAG增强写作] 步骤执行失败", e);
            return new AgentExecResult("步骤执行失败: " + e.getMessage(), AgentState.FAILED);
        }
    }
    
    /**
     * 执行增强写作流程（流式版本）
     */
    public Flux<String> executeWritingPlan(ChapterContentRequest request, 
                                          List<Message> planMessages,
                                          PlanContext context,
                                          List<PlanDetailRes> planSteps) {
        this.context = context;
        this.planSteps = planSteps;
        this.currentStepIndex = 0;
        
        log.info("[RAG增强写作] executeWritingPlan方法被调用，planId: {}", planId);
        
        return Flux.create(sink -> {
            try {
                log.info("[RAG增强写作] 开始执行写作计划，计划ID: {}", planId);
                
                // 初始化工具状态
                toolManager.initializeForPlan(planId);
                
                // 获取所有可用的工具
                List<Object> tools = toolManager.getAllTools();
                log.info("[RAG增强写作] 加载了 {} 个工具", tools.size());
                
                // 构建增强的系统提示词
                String enhancedSystemPrompt = buildEnhancedSystemPrompt();
                
                // 创建带工具的ChatClient
                ChatClient toolEnabledClient = chatClient.mutate()
                        .defaultTools(tools.toArray())
                        .build();
                
                // 执行写作流程
                executeWritingSteps(toolEnabledClient, enhancedSystemPrompt, planMessages, sink);
                
            } catch (Exception e) {
                log.error("[RAG增强写作] 执行失败", e);
                sink.error(e);
            }
        });
    }
    
    /**
     * 构建增强的系统提示词
     */
    private String buildEnhancedSystemPrompt() {
        return """
                你是一位专业的小说创作AI助手，具备以下能力：
                
                ## 核心能力
                1. **信息查询**: 可以查询角色信息、情节设定等背景资料
                2. **RAG搜索**: 可以搜索项目相关文档，确保内容一致性
                3. **文学创作**: 专注于创作高质量、富有文学性的小说内容
                
                ## 可用工具
                - character_query: 查询角色信息，包括性格、背景、目标等
                - plot_query: 查询情节信息，了解故事发展脉络
                - rag_search: 搜索相关文档，获取背景信息和设定细节
                
                ## 写作原则
                1. **信息驱动**: 在写作前主动查询相关信息，确保内容准确
                2. **一致性**: 保持与已有内容的一致性，避免矛盾
                3. **文学性**: 注重语言美感、情感深度和艺术表现
                4. **节奏控制**: 合理安排叙事节奏，营造适当的氛围
                5. **细节丰富**: 通过具体细节增强真实感和沉浸感
                
                ## 工作流程
                1. 分析当前写作任务和上下文
                2. 使用工具查询相关信息（角色、情节、背景等）
                3. 基于查询结果进行深入的写作分析
                4. 创作符合要求的高质量内容
                5. 确保内容与整体故事保持一致
                
                请始终以创作真人水准的高质量小说为目标，充分利用可用工具来增强写作质量。
                """;
    }
    
    /**
     * 执行写作步骤（流式版本）
     */
    private void executeWritingSteps(ChatClient toolEnabledClient,
                                   String systemPrompt,
                                   List<Message> planMessages,
                                   reactor.core.publisher.FluxSink<String> sink) {
        
        try {
            // 构建完整的消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(planMessages);
            
            // 添加当前任务描述
            String taskDescription = buildTaskDescription(null);
            messages.add(new UserMessage(taskDescription));
            
            log.info("[RAG增强写作] 开始流式生成内容");
            
            // 使用流式生成
            Flux<String> contentStream = toolEnabledClient.prompt()
                    .messages(messages)
                    .stream()
                    .content();
            
            // 处理流式输出
            contentStream.subscribe(
                    content -> {
                        log.debug("[RAG增强写作] 生成内容片段: {}", content.substring(0, Math.min(50, content.length())));
                        sink.next(content);
                    },
                    error -> {
                        log.error("[RAG增强写作] 内容生成失败", error);
                        sink.error(error);
                    },
                    () -> {
                        log.info("[RAG增强写作] 内容生成完成");
                        // 清理工具资源
                        toolManager.cleanupPlan(planId);
                        sink.complete();
                    }
            );
            
        } catch (Exception e) {
            log.error("[RAG增强写作] 执行写作步骤失败", e);
            sink.error(e);
        }
    }
    
    /**
     * 构建任务描述
     */
    private String buildTaskDescription(PlanDetailRes currentStep) {
        StringBuilder task = new StringBuilder();
        task.append("## 当前写作任务\n\n");
        
        if (currentStep != null) {
            task.append("**当前步骤**: ").append(currentStep.getPlanContent() != null ? currentStep.getPlanContent() : "写作任务").append("\n");
            if (currentStep.getGoalWordCount() != null) {
                task.append("**目标字数**: ").append(currentStep.getGoalWordCount()).append("字\n");
            }
        }
        
        if (accumulatedContent.length() > 0) {
            task.append("**已写字数**: ").append(accumulatedContent.length()).append("字\n");
        }
        
        task.append("\n## 写作要求\n");
        task.append("1. 在开始写作前，请使用可用工具查询相关信息：\n");
        task.append("   - 使用 character_query 查询涉及的角色信息\n");
        task.append("   - 使用 plot_query 查询相关情节设定\n");
        task.append("   - 使用 rag_search 搜索相关背景信息\n");
        task.append("2. 基于查询结果进行写作分析和规划\n");
        task.append("3. 创作高质量的小说内容，注重文学性和艺术表现\n");
        task.append("4. 确保内容与已有故事保持一致性\n");
        task.append("5. 控制好叙事节奏，营造适当的氛围\n\n");
        
        task.append("请开始执行写作任务。");
        
        return task.toString();
    }
    
    /**
     * 写作分析结果
     */
    @Data
    @NoArgsConstructor
    public static class WritingAnalysis {
        @JsonPropertyDescription("写作重点分析")
        private String focusAnalysis;
        
        @JsonPropertyDescription("角色处理策略")
        private String characterStrategy;
        
        @JsonPropertyDescription("情节推进方式")
        private String plotProgression;
        
        @JsonPropertyDescription("氛围营造方法")
        private String atmosphereBuilding;
        
        @JsonPropertyDescription("预期效果")
        private String expectedEffect;
    }
    
    /**
     * 获取当前状态
     */
    public AgentState getCurrentState() {
        return getState();
    }
    
    /**
     * 获取累积的内容
     */
    public String getAccumulatedContent() {
        return accumulatedContent.toString();
    }
} 