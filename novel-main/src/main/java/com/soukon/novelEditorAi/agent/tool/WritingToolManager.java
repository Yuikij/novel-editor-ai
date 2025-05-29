package com.soukon.novelEditorAi.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写作工具管理器
 * 管理所有可用的写作工具
 */
@Component
@Slf4j
public class WritingToolManager {
    
    private final CharacterQueryTool characterQueryTool;
    private final PlotQueryTool plotQueryTool;
    private final RagSearchTool ragSearchTool;
    
    private final ConcurrentHashMap<String, String> planToolStates = new ConcurrentHashMap<>();
    
    public WritingToolManager(CharacterQueryTool characterQueryTool,
                             PlotQueryTool plotQueryTool,
                             RagSearchTool ragSearchTool) {
        this.characterQueryTool = characterQueryTool;
        this.plotQueryTool = plotQueryTool;
        this.ragSearchTool = ragSearchTool;
        
        log.info("写作工具管理器初始化完成，加载了 3 个工具");
    }
    
    /**
     * 获取所有工具实例
     */
    public List<Object> getAllTools() {
        return List.of(characterQueryTool, plotQueryTool, ragSearchTool);
    }
    
    /**
     * 为特定计划设置工具状态
     */
    public void initializeForPlan(String planId) {
        try {
            characterQueryTool.setPlanId(planId);
            plotQueryTool.setPlanId(planId);
            ragSearchTool.setPlanId(planId);
            
            log.info("为计划 {} 初始化工具状态", planId);
        } catch (Exception e) {
            log.error("初始化计划 {} 的工具状态失败", planId, e);
        }
    }
    
    /**
     * 获取工具状态摘要
     */
    public String getToolStateSummary(String planId) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== 工具状态摘要 ===\n");
        
        try {
            summary.append("角色查询工具: ").append(characterQueryTool.getCurrentToolState()).append("\n");
            summary.append("情节查询工具: ").append(plotQueryTool.getCurrentToolState()).append("\n");
            summary.append("RAG搜索工具: ").append(ragSearchTool.getCurrentToolState()).append("\n");
        } catch (Exception e) {
            log.warn("获取工具状态摘要失败", e);
            summary.append("获取状态失败: ").append(e.getMessage()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 清理计划相关的工具状态
     */
    public void cleanupPlan(String planId) {
        try {
            characterQueryTool.cleanup(planId);
            plotQueryTool.cleanup(planId);
            ragSearchTool.cleanup(planId);
            
            planToolStates.remove(planId);
            
            log.info("清理计划 {} 的工具状态完成", planId);
        } catch (Exception e) {
            log.error("清理计划 {} 的工具状态失败", planId, e);
        }
    }
    
    /**
     * 获取工具数量
     */
    public int getToolCount() {
        return 3;
    }
    
    /**
     * 检查工具是否可用
     */
    public boolean isToolsAvailable() {
        return characterQueryTool != null && plotQueryTool != null && ragSearchTool != null;
    }
} 