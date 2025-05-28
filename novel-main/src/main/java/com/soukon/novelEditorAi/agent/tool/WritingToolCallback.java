package com.soukon.novelEditorAi.agent.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.function.BiFunction;

/**
 * 写作工具回调接口
 * 为小说创作定制的工具调用规范
 */
public interface WritingToolCallback extends BiFunction<String, ToolContext, WritingToolResult> {
    
    /**
     * 获取工具名称
     */
    String getName();
    
    /**
     * 获取工具描述
     */
    String getDescription();
    
    /**
     * 获取工具参数定义
     */
    String getParameters();
    
    /**
     * 获取输入类型
     */
    Class<?> getInputType();
    
    /**
     * 是否直接返回结果
     */
    boolean isReturnDirect();
    
    /**
     * 设置计划ID
     */
    void setPlanId(String planId);
    
    /**
     * 获取当前工具状态
     */
    String getCurrentToolState();
    
    /**
     * 清理资源
     */
    void cleanup(String planId);
    
    /**
     * 转换为Spring AI的ToolCallback
     */
    ToolCallback toSpringAiToolCallback();
} 