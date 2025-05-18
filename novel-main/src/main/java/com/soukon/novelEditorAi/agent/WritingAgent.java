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

    private String previousContent = "无前文";

    private final String reactSystemPrompt = """
            你是一个专业小说写作助手，使用ReAct（思考+行动）模式工作，严格遵循以下流程：
            
            1. 思考：分析当前写作步骤，聚焦于如何在前文基础上进行**深化描写、推进情节、展现角色性格或引入符合当前步骤目标的细节**。提出相关问题并给出简短回答，确保思考具有建设性，避免简单重复或与已有内容高度相似。
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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("思考结果：\n");

            if (questions != null && !questions.isEmpty()) {
                for (int i = 0; i < questions.size(); i++) {
                    QuestionRes q = questions.get(i);
                    sb.append("- 问题").append(i + 1).append("：").append(q.getQuestion()).append("\n");
                    sb.append("  - 回答：").append(q.getAnswer()).append("\n");
                }
            } else {
                sb.append("（无思考问题）\n");
            }

            sb.append("是否完成：").append(isCompleted ? "是" : "否");

            return sb.toString();
        }
    }

    @Data
    @NoArgsConstructor
    public static class QuestionRes {
        private String question;
        private String answer;
    }

    private final String thinkPromptTemplate = """
            
            思考：
            你正在执行写作计划中的第{stepNumber}步：{stepContent}，目标字数为：{goalWordCount}。
            
            你的总目标为：{goal}
            
            你上次完成的内容是:
            {previousContent}
            
            你上次思考的结果是:
            {currentThink}
            
            在继续写作前，结合上文（如果有），并进行结构化思考：
            1. 自动生成3个与此步骤相关的问题，考虑以下方面：
               - 场景的氛围、感官细节或设定。
               - 角色的情绪、动机或行为。
               - 叙事的节奏、语气或情节推进。
            2. 为每个问题提供简短且**具有可操作性的回答**，明确下一步写作中可以加入的具体元素。
            输出格式：
            - 问题1：[生成的问题]
              - 回答：[简短回答]
            - 问题2：[生成的问题]
              - 回答：[简短回答]
            - 问题3：[生成的问题]
              - 回答：[简短回答]
            
            确保问题和回答与上下文和步骤目标一致，为后续写作提供清晰、新颖且具体的指导。避免重复宽泛的思考。
            
            注意：如果你认为该步骤已经完成（即上下文表明章节或场景已自然结束或达到目标字数），则不需要输出问题，并且将isCompleted设为false并且返回空的思考列表。
            
            输出的格式为：{format}
            """;

    private final String actionPromptTemplate = """
            
            根据你的思考结果:{currentThink}，
            
            执行写作计划中的第{stepNumber}步：{stepContent}，目标字数为：{goalWordCount}。
            
            你的总目标为：{goal}
            
            你上次完成的内容是:
            {previousContent}
            
            写作指南：
            - 使用生动、具体的语言，重点营造与思考结果和当前情节发展相适应的氛围。
            - 保持与上下文的连贯性，特别是上一段内容。
            - 融入符合角色性格和情节发展的细节，必要时添加创意元素以增强叙事。
            - 专注具体的剧情和细节以及人物心理描写，不能有过多的总结和重复陈述。
            - 你负责的只是文章的某个片段，不需要每次在最后做总结式结尾。
            
            现在撰写文本，按照要求的字数和思考的问答情况。请直接输出文本内容，而不是结构化内容
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

    private String currentThink = "";
    private int currentStepNumber = 1;
    private List<String> planSteps = new ArrayList<>();
    private int currentWordCount = 0;
    private int targetWordCount = 1000; // 默认目标字数
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
            PromptTemplate promptTemplate = new PromptTemplate(thinkPromptTemplate);
            this.chapterContentRequest.getPlanContext().setPlanState(PlanState.IN_PROGRESS);
            this.stepData.put("format", converter.getFormat());
            this.stepData.put("previousContent", previousContent);
            this.stepData.put("currentThink", currentThink);
            Message thinkMessage = promptTemplate.createMessage(this.stepData);
            List<Message> messageList = new ArrayList<>();
            addThinkPrompt(messageList);
            messageList.add(thinkMessage);
            log.info("[Thinking] 正在思考：{}", messageList);
            String content = llmService.getAgentChatClient(planId)
                    .getChatClient()
                    .prompt(new Prompt(messageList)).call().content();
            log.info("[Thinking] 思考结束：{}", content);
            ThinkRes convert = converter.convert(content);
            currentThink = convert.toString();
            if (convert.getQuestions() != null && !convert.getQuestions().isEmpty() && !convert.isCompleted()) {
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
        stepData.put("previousContent", previousContent);
        stepData.put("currentThink", currentThink);
        Message actionMessage = promptTemplate.createMessage(stepData);
        // 调用LLM生成行动结果
        Prompt prompt = new Prompt(List.of(actionMessage));
        // 这个方法由父类ReActAgent调用，但我们使用自己的执行流程
        log.info("[Thinking] 正在行动：{}", prompt);
        Flux<String> content = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(prompt).stream().content();
        log.info("[Acting] llm调用完成");

        // 创建完成信号
        java.util.concurrent.CountDownLatch consumptionLatch = new java.util.concurrent.CountDownLatch(1);

        // 创建一个StringBuilder来保存完整内容
        StringBuilder fullContent = new StringBuilder();

        // 将原始流包装成一个新的流，用于捕获内容
        Flux<String> contentWithCapture = content
                .doOnNext(chunk -> {
                    try {
                        fullContent.append(chunk);
                        if (fullContent.length() % 100 == 0) {
                            log.debug("[Acting] Captured content length so far: {} characters", fullContent.length());
                        }
                    } catch (Exception e) {
                        log.error("[Acting] Error processing content chunk: {}", e.getMessage(), e);
                    }
                })
                .doOnComplete(() -> {
                    log.info("[Acting] 行动结束：{}", fullContent);
                })
                .doOnError(error -> {
                    log.error("[Acting] Error in content stream: {}", error.getMessage(), error);
                });

        // 将等待信号保存到PlanContext
        this.chapterContentRequest.getPlanContext().setCompletionLatch(consumptionLatch);
        this.chapterContentRequest.getPlanContext().setPlanStream(contentWithCapture);
        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.GENERATING);

        try {
            // 等待前端完成消费（前端需要主动调用countDown）
            log.info("[Acting] 等待前端消费完毕...");
            consumptionLatch.await(5, java.util.concurrent.TimeUnit.MINUTES);  // 添加超时，最多等待5分钟
            log.info("[Acting] 前端消费完毕或等待超时，继续执行");
        } catch (InterruptedException e) {
            log.error("[Acting] 等待前端消费过程被中断", e);
            Thread.currentThread().interrupt();
        }

        this.chapterContentRequest.getPlanContext().setPlanState(PlanState.IN_PROGRESS);
        this.chapterContentRequest.getPlanContext().setPlanStream(null);
        previousContent = fullContent.toString();
        log.info("[Acting] Total captured content length: {} characters", fullContent.length());
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
