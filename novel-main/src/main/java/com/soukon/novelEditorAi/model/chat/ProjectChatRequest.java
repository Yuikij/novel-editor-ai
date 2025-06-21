package com.soukon.novelEditorAi.model.chat;

import lombok.Data;

/**
 * 项目对话请求
 */
@Data
public class ProjectChatRequest {
    
    /**
     * 用户消息
     */
    private String message;
    
    /**
     * 会话ID（可选，用于多会话管理）
     */
    private String sessionId;
    
    /**
     * 是否启用向量检索
     */
    private Boolean enableVectorSearch = true;
    
    /**
     * 向量检索最大结果数
     */
    private Integer maxVectorResults = 10;
    
    /**
     * 向量检索相似度阈值
     */
    private Float similarityThreshold = 0.7f;
    
    /**
     * 对话模式
     */
    private ChatMode mode = ChatMode.CREATIVE;
    
    /**
     * 温度参数（控制创造性）
     */
    private Float temperature = 0.8f;
    
    /**
     * 最大输出长度
     */
    private Integer maxTokens = 2000;
    
    /**
     * 对话模式枚举
     */
    public enum ChatMode {
        CREATIVE("创作模式"),
        ANALYSIS("分析模式"), 
        PLANNING("规划模式"),
        REVIEW("审查模式");
        
        private final String description;
        
        ChatMode(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
} 