package com.soukon.novelEditorAi.model.chat;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * 项目对话上下文
 */
@Data
public class ProjectChatContext {
    
    /**
     * 项目ID
     */
    private Long projectId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 对话历史
     */
    private List<ChatMessage> messages = new ArrayList<>();
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 对话轮数
     */
    private Integer turnCount = 0;
    
    /**
     * 总token数
     */
    private Integer totalTokens = 0;
    
    /**
     * 对话消息
     */
    @Data
    public static class ChatMessage {
        /**
         * 消息角色
         */
        private MessageRole role;
        
        /**
         * 消息内容
         */
        private String content;
        
        /**
         * 创建时间
         */
        private LocalDateTime timestamp;
        
        /**
         * token数量
         */
        private Integer tokens;
        
        /**
         * 相关的向量检索结果
         */
        private List<VectorSearchResult> vectorResults;
        
        public ChatMessage() {
            this.timestamp = LocalDateTime.now();
        }
        
        public ChatMessage(MessageRole role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = LocalDateTime.now();
        }
    }
    
    /**
     * 消息角色枚举
     */
    public enum MessageRole {
        USER("用户"),
        ASSISTANT("助手"),
        SYSTEM("系统");
        
        private final String description;
        
        MessageRole(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 向量检索结果
     */
    @Data
    public static class VectorSearchResult {
        /**
         * 实体类型
         */
        private String entityType;
        
        /**
         * 实体ID
         */
        private Long entityId;
        
        /**
         * 相似度分数
         */
        private Float similarity;
        
        /**
         * 内容片段
         */
        private String content;
        
        /**
         * 元数据
         */
        private String metadata;
    }
    
    /**
     * 添加消息
     */
    public void addMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage(role, content);
        this.messages.add(message);
        this.turnCount++;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 添加消息（带向量检索结果）
     */
    public void addMessage(MessageRole role, String content, List<VectorSearchResult> vectorResults) {
        ChatMessage message = new ChatMessage(role, content);
        message.setVectorResults(vectorResults);
        this.messages.add(message);
        this.turnCount++;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取最近的消息
     */
    public List<ChatMessage> getRecentMessages(int limit) {
        int size = messages.size();
        int start = Math.max(0, size - limit);
        return messages.subList(start, size);
    }
    
    /**
     * 清空对话历史
     */
    public void clear() {
        this.messages.clear();
        this.turnCount = 0;
        this.totalTokens = 0;
        this.updatedAt = LocalDateTime.now();
    }
} 