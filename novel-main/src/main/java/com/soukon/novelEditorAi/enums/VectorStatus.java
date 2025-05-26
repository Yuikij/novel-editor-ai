package com.soukon.novelEditorAi.enums;

/**
 * 向量化状态枚举
 */
public enum VectorStatus {
    /**
     * 未索引
     */
    NOT_INDEXED("NOT_INDEXED", "未索引"),
    
    /**
     * 索引中
     */
    INDEXING("INDEXING", "索引中"),
    
    /**
     * 已索引
     */
    INDEXED("INDEXED", "已索引"),
    
    /**
     * 索引失败
     */
    FAILED("FAILED", "索引失败");
    
    private final String code;
    private final String description;
    
    VectorStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static VectorStatus fromCode(String code) {
        for (VectorStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return NOT_INDEXED;
    }
} 