package com.soukon.novelEditorAi.agent.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 写作工具执行结果
 */
@Data
@NoArgsConstructor
public class WritingToolResult {
    
    /**
     * 执行结果内容
     */
    private String result;
    
    /**
     * 是否执行成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 工具状态信息
     */
    private String toolState;
    
    /**
     * 额外的元数据
     */
    private Object metadata;
    
    public WritingToolResult(String result) {
        this.result = result;
        this.success = true;
    }
    
    public WritingToolResult(String result, boolean success) {
        this.result = result;
        this.success = success;
    }
    
    public WritingToolResult(String result, boolean success, String errorMessage) {
        this.result = result;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 创建成功结果
     */
    public static WritingToolResult success(String result) {
        return new WritingToolResult(result, true);
    }
    
    /**
     * 创建失败结果
     */
    public static WritingToolResult failure(String errorMessage) {
        return new WritingToolResult("", false, errorMessage);
    }
    
    /**
     * 创建带状态的结果
     */
    public static WritingToolResult withState(String result, String toolState) {
        WritingToolResult toolResult = new WritingToolResult(result, true);
        toolResult.setToolState(toolState);
        return toolResult;
    }
} 