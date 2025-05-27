package com.soukon.novelEditorAi.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.soukon.novelEditorAi.llm.LlmService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 写作质量评估器
 * 用于评估生成文本的质量并提供改进建议
 */
@Slf4j
public class WritingQualityEvaluator {
    
    private final LlmService llmService;
    private final String planId;
    
    public WritingQualityEvaluator(LlmService llmService, String planId) {
        this.llmService = llmService;
        this.planId = planId;
    }
    
    /**
     * 评估文本质量
     */
    public QualityEvaluation evaluateQuality(String content, String context) {
        String evaluationPrompt = buildEvaluationPrompt(content, context);
        
        BeanOutputConverter<QualityEvaluation> converter = new BeanOutputConverter<>(QualityEvaluation.class);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(getEvaluationSystemPrompt()));
        messages.add(new UserMessage(evaluationPrompt + "\n\n输出格式：" + converter.getFormat()));
        
        String result = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(new Prompt(messages))
                .call()
                .content();
        
        return converter.convert(result);
    }
    
    /**
     * 改进文本质量
     */
    public String improveContent(String content, QualityEvaluation evaluation) {
        if (evaluation.getOverallScore() >= 8) {
            // 质量已经很高，不需要改进
            return content;
        }
        
        String improvementPrompt = buildImprovementPrompt(content, evaluation);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(getImprovementSystemPrompt()));
        messages.add(new UserMessage(improvementPrompt));
        
        String improvedContent = llmService.getAgentChatClient(planId)
                .getChatClient()
                .prompt(new Prompt(messages))
                .call()
                .content();
        
        log.info("文本质量改进完成，原始评分: {}, 改进建议: {}", 
                evaluation.getOverallScore(), evaluation.getImprovementSuggestions());
        
        return improvedContent;
    }
    
    /**
     * 构建评估提示词
     */
    private String buildEvaluationPrompt(String content, String context) {
        return String.format("""
            ## 文本质量评估任务
            
            请评估以下小说文本的质量，重点关注文学性和可读性：
            
            ### 待评估文本
            %s
            
            ### 上下文信息
            %s
            
            ### 评估维度
            请从以下维度进行评估（1-10分）：
            1. **语言美感**：词汇选择、句式变化、修辞运用
            2. **情节推进**：故事发展的自然性和吸引力
            3. **人物刻画**：角色的立体性和真实感
            4. **环境描写**：场景营造和氛围渲染
            5. **情感表达**：情感的深度和感染力
            6. **逻辑连贯**：前后文的逻辑性和连贯性
            7. **创新性**：表达方式的新颖性和独特性
            8. **可读性**：文本的流畅性和易读性
            
            请给出具体的评分和改进建议。
            """, content, context);
    }
    
    /**
     * 构建改进提示词
     */
    private String buildImprovementPrompt(String content, QualityEvaluation evaluation) {
        return String.format("""
            ## 文本改进任务
            
            根据质量评估结果，改进以下文本：
            
            ### 原始文本
            %s
            
            ### 评估结果
            - 整体评分：%d/10
            - 语言美感：%d/10
            - 情节推进：%d/10
            - 人物刻画：%d/10
            - 环境描写：%d/10
            - 情感表达：%d/10
            - 逻辑连贯：%d/10
            - 创新性：%d/10
            - 可读性：%d/10
            
            ### 改进建议
            %s
            
            ### 改进要求
            1. 保持原文的核心内容和情节发展
            2. 重点改进评分较低的维度
            3. 提升语言的文学性和表达力
            4. 增强文本的感染力和可读性
            5. 保持文本长度基本不变
            
            请输出改进后的文本，不要包含任何解释或标记。
            """,
            content,
            evaluation.getOverallScore(),
            evaluation.getLanguageBeauty(),
            evaluation.getPlotProgression(),
            evaluation.getCharacterDepiction(),
            evaluation.getEnvironmentDescription(),
            evaluation.getEmotionalExpression(),
            evaluation.getLogicalCoherence(),
            evaluation.getInnovativeness(),
            evaluation.getReadability(),
            evaluation.getImprovementSuggestions());
    }
    
    /**
     * 获取评估系统提示词
     */
    private String getEvaluationSystemPrompt() {
        return """
            你是一位资深的文学评论家和编辑，拥有丰富的小说评估经验。
            
            你的任务是客观、专业地评估小说文本的质量，重点关注：
            1. 文学价值和艺术性
            2. 读者体验和可读性
            3. 技巧运用和表达效果
            4. 创新性和独特性
            
            评分标准：
            - 9-10分：优秀，具有很高的文学价值
            - 7-8分：良好，质量较高但有改进空间
            - 5-6分：一般，基本合格但需要改进
            - 3-4分：较差，存在明显问题
            - 1-2分：很差，需要大幅改进
            
            请给出客观、具体的评估和建议。
            """;
    }
    
    /**
     * 获取改进系统提示词
     */
    private String getImprovementSystemPrompt() {
        return """
            你是一位才华横溢的小说作家和编辑，擅长改进和润色文学作品。
            
            你的任务是根据评估结果改进文本，重点关注：
            1. 提升语言的文学性和美感
            2. 增强情节的吸引力和推进力
            3. 深化人物刻画和情感表达
            4. 优化环境描写和氛围营造
            5. 保持逻辑连贯和可读性
            
            改进原则：
            - 保持原文的核心内容和结构
            - 重点改进评分较低的方面
            - 追求语言的精准和优美
            - 增强文本的感染力和艺术性
            - 确保改进后的文本更加出色
            
            请输出高质量的改进文本。
            """;
    }
    
    /**
     * 质量评估结果
     */
    @Data
    @NoArgsConstructor
    public static class QualityEvaluation {
        @JsonPropertyDescription("整体评分（1-10）")
        private Integer overallScore;
        
        @JsonPropertyDescription("语言美感评分（1-10）")
        private Integer languageBeauty;
        
        @JsonPropertyDescription("情节推进评分（1-10）")
        private Integer plotProgression;
        
        @JsonPropertyDescription("人物刻画评分（1-10）")
        private Integer characterDepiction;
        
        @JsonPropertyDescription("环境描写评分（1-10）")
        private Integer environmentDescription;
        
        @JsonPropertyDescription("情感表达评分（1-10）")
        private Integer emotionalExpression;
        
        @JsonPropertyDescription("逻辑连贯评分（1-10）")
        private Integer logicalCoherence;
        
        @JsonPropertyDescription("创新性评分（1-10）")
        private Integer innovativeness;
        
        @JsonPropertyDescription("可读性评分（1-10）")
        private Integer readability;
        
        @JsonPropertyDescription("具体的改进建议")
        private String improvementSuggestions;
        
        @JsonPropertyDescription("主要优点")
        private String strengths;
        
        @JsonPropertyDescription("主要问题")
        private String weaknesses;
    }
} 