/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soukon.novelEditorAi.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanRes;
import com.soukon.novelEditorAi.model.chapter.PlanState;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

public class WritingAgent extends ReActAgent {


    private static final Logger log = LoggerFactory.getLogger(WritingAgent.class);

    private final String reactSystemPrompt = """
            你是一个专业小说写作助手，使用ReAct（思考+行动）模式工作，严格遵循以下流程：
            
            1. 思考：分析当前写作步骤，提出相关问题并给出简短回答
            2. 行动：基于思考结果，创作对应的小说内容
            3. 评估：判断是否达到终止条件，决定继续或结束
            
            每个步骤要有明确的标记和格式。
            
            全局计划完成情节：
            {global}
            全局步骤计划:
            {plan}
            
            """;

    @Data
    @NoArgsConstructor
    public static class ThinkRes {
        @JsonPropertyDescription("问题与回答列表")
        private List<QuestionRes> questions;
        @JsonPropertyDescription("该步骤已经完成")
        private boolean isCompleted;
    }

    @Data
    @NoArgsConstructor
    public static class QuestionRes {
        private String question;
        private String answer;
    }

    private final String thinkPromptTemplate = """
            
            思考：
            你正在执行写作计划中的第{stepNumber}步：{stepContent}。
            
            在开始写作前，进行结构化思考：
            1. 自动生成3个与此步骤相关的问题，考虑以下方面：
               - 场景的氛围、感官细节或设定。
               - 角色的情绪、动机或行为。
               - 叙事的节奏、语气或情节推进。
            2. 为每个问题提供简短的回答（每回答50字以内）。
            
            输出格式：
            - 问题1：[生成的问题]
              - 回答：[简短回答]
            - 问题2：[生成的问题]
              - 回答：[简短回答]
            - 问题3：[生成的问题]
              - 回答：[简短回答]
            
            确保问题和回答与上下文和步骤目标一致，为后续写作提供清晰的指导。
            
            注意：如果你认为该步骤已经完成，则不需要输出问题，并且将isCompleted设为false。
            
            输出的格式为：{format}
            """;

    private final String actionPromptTemplate = """
            
            根据你的思考结果，执行写作计划中的第{stepNumber}步：{stepContent}。
            
            写作指南：
            - 使用生动、具体的语言，营造(根据上下文推断的语气，例如"悬疑、紧张"或"温馨、感人")的氛围。
            - 保持与上下文的连贯性，特别是上一段内容。
            - 融入符合角色性格和情节发展的细节，必要时添加创意元素以增强叙事。
            
            现在撰写文本。
            """;

    private final String evaluatePromptTemplate = """
            
            完成当前步骤后，返回写作计划并执行下一步。
            
            终止条件：
            - 计划中的所有步骤均已完成，或
            - 总字数达到{targetWordCount}字，或
            - 上下文表明章节或场景已自然结束（例如，达到情节高潮或转折点）。
            
            如果继续，简要说明下一步的重点；如果停止，说明原因并总结已完成的内容。
            """;

    private BeanOutputConverter<ThinkRes> converter = new BeanOutputConverter<>(ThinkRes.class);
    private ToolCallbackProvider toolCallbackProvider;
    private ChatResponse response;
    private Prompt userPrompt;


    private int currentStepNumber = 1;
    private List<String> planSteps = new ArrayList<>();
    private int currentWordCount = 0;
    private int targetWordCount = 1000; // 默认目标字数
    private String previousContent = "无前文";
    private String mood = "自然流畅";
    private StringBuilder generatedContent = new StringBuilder();


    public WritingAgent(LlmService llmService, ChapterContentRequest request) {
        super(llmService, request);
    }

    public Flux<String> run(String planStep, String planId, ChapterContentRequest request) {
        this.planId = planId;

        this.planSteps.add(planStep);

        // 从请求中获取目标字数，如果有的话
        if (request.getWordCountSuggestion() != null) {
            this.targetWordCount = request.getWordCountSuggestion();
        }

        // 创建一个Flux用于返回生成的内容
        List<String> contentPieces = new ArrayList<>();
        AtomicInteger stepCounter = new AtomicInteger(1);

        // 执行所有步骤，直到满足终止条件
        while (currentStepNumber <= planSteps.size() && currentWordCount < targetWordCount) {
            AgentExecResult result = executeWritingStep(currentStepNumber);
            if (result.getResult() != null && !result.getResult().isEmpty()) {
                contentPieces.add(result.getResult());
                log.info("已完成第{}步写作，当前总字数: {}", currentStepNumber, currentWordCount);
                currentStepNumber++;
            }

            if (result.getState() == AgentState.COMPLETED) {
                log.info("写作完成: {}", result.getResult());
                break;
            }
        }

        return Flux.fromIterable(contentPieces);
    }

    private AgentExecResult executeWritingStep(int stepNumber) {
        try {
            // 准备当前步骤数据
            stepData.clear();
            stepData.put("stepNumber", stepNumber);
            stepData.put("stepContent", planSteps.get(stepNumber - 1));
            stepData.put("previousContent", previousContent);
            stepData.put("mood", mood);
            stepData.put("wordCount", "150-300");
            stepData.put("targetWordCount", String.valueOf(targetWordCount));

            // 执行思考阶段
            String thoughtResult = executeThinkPhase();
            log.debug("思考阶段结果: {}", thoughtResult);

            // 执行行动阶段
            String actionResult = executeActionPhase();
            log.debug("行动阶段结果: {}", actionResult);

            // 更新上下文
            currentWordCount += actionResult.length();
            previousContent = getLastParagraph(actionResult);
            generatedContent.append(actionResult).append("\n\n");

            // 判断是否需要终止
            boolean shouldTerminate = currentStepNumber >= planSteps.size() ||
                    currentWordCount >= targetWordCount;

            if (shouldTerminate) {
                return new AgentExecResult(actionResult, AgentState.COMPLETED);
            } else {
                return new AgentExecResult(actionResult, AgentState.IN_PROGRESS);
            }

        } catch (Exception e) {
            log.error("写作步骤执行失败: {}", e.getMessage(), e);
            return new AgentExecResult("写作执行失败: " + e.getMessage(), AgentState.FAILED);
        }
    }

    private String executeThinkPhase() {
        PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
        Message thinkMessage = promptTemplate.createMessage(stepData);

        // 调用LLM生成思考结果
        Prompt prompt = new Prompt(List.of(thinkMessage));
        String result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt)
                .call()
                .content();

        return result;
    }

    private String executeActionPhase() {
        PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
        Message actionMessage = promptTemplate.createMessage(stepData);

        // 调用LLM生成行动结果
        Prompt prompt = new Prompt(List.of(actionMessage));
        Flux<String> result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt)
                .stream().content();

        PlanContext planContext = chapterContentRequest.getPlanContext();
        planContext.setPlanStream(result);
        return "";
    }

    private String getLastParagraph(String text) {
        if (text == null || text.isEmpty()) {
            return "无前文";
        }

        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length > 0) {
            String lastParagraph = paragraphs[paragraphs.length - 1].trim();
            // 如果段落太长，只返回最后100个字
            if (lastParagraph.length() > 100) {
                return "..." + lastParagraph.substring(lastParagraph.length() - 100);
            }
            return lastParagraph;
        }

        return text.length() > 100 ? "..." + text.substring(text.length() - 100) : text;
    }

    @Override
    protected boolean think() {
        try {
            log.info("[Thinking] 正在思考");
            PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
            this.chapterContentRequest.getPlanContext().setPlanState(PlanState.IN_PROGRESS);
            this.stepData.put("format", converter.getFormat());
            Message thinkMessage = promptTemplate.createMessage(this.stepData);
            List<Message> messageList = new ArrayList<>();
            addThinkPrompt(messageList);
            messageList.add(thinkMessage);
            String content = llmService.getAgentChatClient(planId)
                    .getChatClient()
                    .prompt(new Prompt(messageList)).call().content();
            log.info("[Thinking] 思考结束：{}",content);
            ThinkRes convert = converter.convert(content);
            if (convert != null && convert.getQuestions() != null && !convert.getQuestions().isEmpty() && !convert.isCompleted()) {
                return true;
            }

        } catch (Exception e) {
            log.error("思考阶段执行失败: {}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    protected AgentExecResult act() {

        PromptTemplate promptTemplate = new PromptTemplate(actionPromptTemplate);
        Message actionMessage = promptTemplate.createMessage(stepData);

        // 调用LLM生成行动结果
        Prompt prompt = new Prompt(List.of(actionMessage));
        // 这个方法由父类ReActAgent调用，但我们使用自己的执行流程
        Flux<String> content = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt).stream().content();
        log.info("[Acting] llm调用完成");
        
        // 创建完成信号
        java.util.concurrent.CountDownLatch consumptionLatch = new java.util.concurrent.CountDownLatch(1);
        
        // 将等待信号保存到PlanContext
        this.chapterContentRequest.getPlanContext().setCompletionLatch(consumptionLatch);
        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.GENERATING);
        
        // 创建一个StringBuilder来收集完整内容
        StringBuilder fullContent = new StringBuilder();
        
        // 创建一个可缓存的热流，避免多次订阅问题，保留上下文
        Flux<String> cachedContent = content
            .doOnNext(chunk -> fullContent.append(chunk))
            .doOnComplete(() -> {
                // 流完成后，将完整内容传给think方法
                try {
                    llmService.getAgentChatClient(getPlanId()).getMemory().add(getPlanId(), new UserMessage(fullContent.toString()));
                } catch (Exception e) {
                    log.error("[Acting] 添加内容到记忆失败", e);
                }
                
                // 设置完整内容供前端获取
                this.chapterContentRequest.getPlanContext().setCompletedContent(fullContent.toString());
                log.info("[Acting] 内容生成完毕，等待前端消费");
            })
            .cache();  // 缓存流，使其可以被多次订阅
            
        // 设置包装后的流供前端消费
        this.chapterContentRequest.getPlanContext().setPlanStream(cachedContent);
        
        try {
            // 订阅流以确保它开始执行，但不阻塞
            cachedContent.subscribe();
            
            // 等待前端完成消费（前端需要主动调用countDown）
            log.info("[Acting] 等待前端消费完毕...");
            consumptionLatch.await(5, java.util.concurrent.TimeUnit.MINUTES);  // 添加超时，最多等待5分钟
            log.info("[Acting] 前端消费完毕或等待超时，继续执行");
        } catch (InterruptedException e) {
            log.error("[Acting] 等待前端消费过程被中断", e);
            Thread.currentThread().interrupt();
        }
        
        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.IN_PROGRESS);
        
        // 返回已完成的内容
        return new AgentExecResult(fullContent.toString(), AgentState.IN_PROGRESS);
    }

    @Override
    protected Message getNextStepWithEnvMessage() {
        return new UserMessage("继续");
    }

    @Override
    public String getName() {
        return "WritingAgent";
    }

    @Override
    public String getDescription() {
        return "一个使用ReAct模式进行小说写作的智能代理";
    }

    @Override
    protected Message addThinkPrompt(List<Message> messages) {
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(reactSystemPrompt);
        Map<String, Object> data = new HashMap<>();
        data.put("plan", chapterContentRequest.getPlan());
        data.put("global", chapterContentRequest.getGlobalContext() == null ? "无全局计划" :
                chapterContentRequest.getGlobalContext());
        Message systemMessage = promptTemplate.createMessage(data);
        messages.add(systemMessage);
        return systemMessage;
    }


    public String getPlanId() {
        return this.planId;
    }
}
