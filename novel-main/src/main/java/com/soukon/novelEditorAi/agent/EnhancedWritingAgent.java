package com.soukon.novelEditorAi.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 增强版写作代理
 * 专注于高质量文学创作，采用更自然的写作流程
 */
@Slf4j
public class EnhancedWritingAgent {

    private final LlmService llmService;
    private final ChapterContentRequest request;
    private final String planId;

    // 写作状态
    private StringBuilder accumulatedContent = new StringBuilder();
    private int currentWordCount = 0;
    private int targetWordCount = 1000;
    private String lastParagraph = "";

    // 写作质量控制
    private static final int MIN_SEGMENT_WORDS = 150;
    private static final int MAX_SEGMENT_WORDS = 400;
    private static final double WORD_COUNT_TOLERANCE = 0.1; // 10%容错率

    public EnhancedWritingAgent(LlmService llmService, ChapterContentRequest request) {
        this.llmService = llmService;
        this.request = request;
        this.planId = request.getPlanContext().getPlanId();

        if (request.getWordCountSuggestion() != null) {
            this.targetWordCount = request.getWordCountSuggestion();
        }
    }

    /**
     * 执行写作计划
     */
    public void executeWritingPlan(List<PlanDetailRes> planSteps) {
        PlanContext planContext = request.getPlanContext();

        try {
            planContext.setPlanState(PlanState.IN_PROGRESS);
            planContext.setMessage("开始执行写作计划");
            planContext.setProgress(10);

            for (int i = 0; i < planSteps.size(); i++) {
                PlanDetailRes step = planSteps.get(i);
                int stepProgress = 10 + (i * 80 / planSteps.size());

                planContext.setMessage("正在执行第" + (i + 1) + "步：" + step.getPlanContent());
                planContext.setProgress(stepProgress);

                executeWritingStep(step, i + 1, planSteps.size());

                // 检查是否达到目标字数
                if (isTargetWordCountReached()) {
                    log.info("已达到目标字数 {}，提前结束写作", targetWordCount);
                    break;
                }
            }

            planContext.setPlanState(PlanState.COMPLETED);
            planContext.setMessage("写作完成");
            planContext.setProgress(100);

        } catch (Exception e) {
            log.error("写作执行失败", e);
            planContext.setPlanState(PlanState.COMPLETED);
            planContext.setMessage("写作失败：" + e.getMessage());
            throw new RuntimeException("写作执行失败", e);
        }
    }

    /**
     * 执行单个写作步骤
     */
    private void executeWritingStep(PlanDetailRes step, int stepNumber, int totalSteps) {
        // 1. 创作前分析
        WritingAnalysis analysis = analyzeWritingContext(step, stepNumber, totalSteps);

        // 2. 生成内容
        String content = generateContent(step, analysis, stepNumber);

        log.info("生成内容：{}", content);

        // 3. 更新状态
        updateWritingState(content);

        // 4. 流式输出给前端
        streamContentToFrontend(content);
    }

    /**
     * 分析写作上下文
     */
    private WritingAnalysis analyzeWritingContext(PlanDetailRes step, int stepNumber, int totalSteps) {
        String analysisPrompt = buildAnalysisPrompt(step, stepNumber, totalSteps);

        BeanOutputConverter<WritingAnalysis> converter = new BeanOutputConverter<>(WritingAnalysis.class);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(getAnalysisSystemPrompt()));
        messages.add(new UserMessage(analysisPrompt + "\n\n输出格式：" + converter.getFormat()));

        log.info("开始分析写作上下文：{}", messages);

        String result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(new Prompt(messages))
                .call()
                .content();
        log.info("分析写作上下文结果：{}", result);
        return converter.convert(result);
    }

    /**
     * 生成写作内容
     */
    private String generateContent(PlanDetailRes step, WritingAnalysis analysis, int stepNumber) {
        String contentPrompt = buildContentPrompt(step, analysis, stepNumber);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(getContentSystemPrompt()));
        messages.add(new UserMessage(contentPrompt));

        log.info("开始生成写作内容：{}", messages);

        // 使用流式生成
        Flux<String> contentStream = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(new Prompt(messages))
                .stream()
                .content();

        // 收集完整内容
        StringBuilder fullContent = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        contentStream
                .doOnNext(fullContent::append)
                .doOnComplete(latch::countDown)
                .doOnError(error -> {
                    log.error("内容生成失败", error);
                    latch.countDown();
                })
                .subscribe();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("内容生成被中断", e);
        }

        return fullContent.toString();
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(PlanDetailRes step, int stepNumber, int totalSteps) {
        return String.format("""
                        ## 写作分析任务
                                    
                        当前步骤：第%d步（共%d步）
                        步骤内容：%s
                        目标字数：%d字
                                    
                        ## 当前写作状态
                        已写字数：%d字
                        目标总字数：%d字
                        剩余字数：%d字
                                    
                        ## 上文内容（最后一段）
                        %s
                                    
                        ## 分析要求
                        请分析当前写作情况，重点考虑：
                        1. 这一步应该写多少字才合理？
                        2. 应该采用什么样的叙事节奏？
                        3. 重点描写什么内容？
                        4. 如何与上文自然衔接？
                        5. 如何为下一步做铺垫？
                        """,
                stepNumber, totalSteps, step.getPlanContent(), step.getGoalWordCount(),
                currentWordCount, targetWordCount, targetWordCount - currentWordCount,
                lastParagraph.isEmpty() ? "（无上文）" : lastParagraph);
    }

    /**
     * 构建内容生成提示词
     */
    private String buildContentPrompt(PlanDetailRes step, WritingAnalysis analysis, int stepNumber) {
        return String.format("""
                        ## 写作任务
                                    
                        根据以下分析结果，创作高质量的小说内容：
                                    
                        ### 分析结果
                        建议字数：%d字
                        叙事节奏：%s
                        重点内容：%s
                        衔接方式：%s
                        铺垫要素：%s
                                    
                        ### 写作要求
                        1. 严格按照建议字数创作（误差不超过20字）
                        2. 采用建议的叙事节奏
                        3. 重点描写指定内容
                        4. 与上文自然衔接
                        5. 为后续情节做好铺垫
                                    
                        ### 质量标准
                        - 语言生动，富有文学性
                        - 情节推进自然流畅
                        - 人物刻画深入细腻
                        - 环境描写恰到好处
                        - 对话真实可信
                                    
                        ### 上文内容
                        %s
                                    
                        请直接输出小说内容，不要包含任何解释或标记。
                        """,
                analysis.getSuggestedWordCount(),
                analysis.getNarrativePace(),
                analysis.getFocusContent(),
                analysis.getConnectionMethod(),
                analysis.getForeshadowing(),
                lastParagraph.isEmpty() ? "（开始写作）" : lastParagraph);
    }

    /**
     * 更新写作状态
     */
    private void updateWritingState(String content) {
        accumulatedContent.append(content).append("\n\n");
        currentWordCount += content.length();

        // 更新最后一段
        String[] paragraphs = content.split("\n\n");
        if (paragraphs.length > 0) {
            lastParagraph = paragraphs[paragraphs.length - 1].trim();
            // 限制长度，避免上下文过长
            if (lastParagraph.length() > 200) {
                lastParagraph = "..." + lastParagraph.substring(lastParagraph.length() - 200);
            }
        }
    }

    /**
     * 流式输出内容给前端
     */
    private void streamContentToFrontend(String content) {
        PlanContext planContext = request.getPlanContext();

        // 创建内容流
        Flux<String> contentStream = Flux.just(content);

        // 创建完成信号
        CountDownLatch latch = new CountDownLatch(1);

        planContext.setCompletionLatch(latch);
        planContext.setPlanStream(contentStream);
        planContext.setPlanState(PlanState.GENERATING);

        try {
            // 等待前端消费完成
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待前端消费被中断", e);
        }

        planContext.setPlanStream(null);
        planContext.setPlanState(PlanState.IN_PROGRESS);
    }

    /**
     * 检查是否达到目标字数
     */
    private boolean isTargetWordCountReached() {
        double progress = (double) currentWordCount / targetWordCount;
        return progress >= (1.0 - WORD_COUNT_TOLERANCE);
    }

    /**
     * 获取分析阶段系统提示词
     */
    private String getAnalysisSystemPrompt() {
        return """
                你是一位经验丰富的小说编辑，擅长分析写作进度和规划写作策略。
                            
                你的任务是分析当前的写作情况，并给出具体的写作建议：
                1. 根据整体进度和剩余字数，建议这一步应该写多少字
                2. 根据情节发展需要，建议采用什么叙事节奏（如：缓慢铺垫、快速推进、高潮迭起等）
                3. 根据步骤要求，建议重点描写什么内容
                4. 根据上文内容，建议如何自然衔接
                5. 根据后续发展，建议如何做铺垫
                            
                请给出具体、可操作的建议，避免空泛的描述。
                """;
    }

    /**
     * 获取内容生成系统提示词
     */
    private String getContentSystemPrompt() {
        return """
                你是一位才华横溢的小说作家，擅长创作引人入胜的文学作品。
                            
                写作原则：
                1. 文字优美，富有文学性和感染力
                2. 情节推进自然，逻辑清晰
                3. 人物刻画立体，性格鲜明
                4. 环境描写生动，营造氛围
                5. 对话真实自然，符合人物身份
                            
                技巧要求：
                - 多用具体的细节描写，少用抽象概括
                - 通过行动和对话展现人物性格
                - 运用感官描写增强沉浸感
                - 控制叙事节奏，张弛有度
                - 语言简洁有力，避免冗余
                            
                请严格按照要求创作，确保内容质量。
                """;
    }

    /**
     * 写作分析结果
     */
    @Data
    @NoArgsConstructor
    public static class WritingAnalysis {
        @JsonPropertyDescription("建议的写作字数")
        private Integer suggestedWordCount;

        @JsonPropertyDescription("建议的叙事节奏（如：缓慢铺垫、快速推进、高潮迭起等）")
        private String narrativePace;

        @JsonPropertyDescription("重点描写的内容")
        private String focusContent;

        @JsonPropertyDescription("与上文的衔接方式")
        private String connectionMethod;

        @JsonPropertyDescription("为后续情节做的铺垫")
        private String foreshadowing;
    }

    /**
     * 计划详情响应（临时定义，应该引用实际的类）
     */
    @Data
    @NoArgsConstructor
    public static class PlanDetailRes {
        private Integer goalWordCount;
        private String planContent;

        public PlanDetailRes(Integer goalWordCount, String planContent) {
            this.goalWordCount = goalWordCount;
            this.planContent = planContent;
        }
    }
} 