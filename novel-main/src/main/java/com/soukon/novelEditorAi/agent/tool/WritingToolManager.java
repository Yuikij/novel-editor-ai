package com.soukon.novelEditorAi.agent.tool;

import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.service.PlotService;
import com.soukon.novelEditorAi.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 写作工具管理器
 * 统一管理所有写作相关的工具
 */
@Component
@Slf4j
public class WritingToolManager {
    
    private final CharacterMapper characterMapper;
    private final PlotService plotService;
    private final RagService ragService;
    
    // 工具实例缓存
    private final Map<String, WritingToolCallback> toolInstances = new HashMap<>();
    
    @Autowired
    public WritingToolManager(CharacterMapper characterMapper, 
                             PlotService plotService, 
                             RagService ragService) {
        this.characterMapper = characterMapper;
        this.plotService = plotService;
        this.ragService = ragService;
        
        initializeTools();
    }
    
    /**
     * 初始化所有工具
     */
    private void initializeTools() {
        // 角色查询工具
        CharacterQueryTool characterQueryTool = new CharacterQueryTool(characterMapper);
        toolInstances.put(characterQueryTool.getName(), characterQueryTool);
        
        // 情节查询工具
        PlotQueryTool plotQueryTool = new PlotQueryTool(plotService);
        toolInstances.put(plotQueryTool.getName(), plotQueryTool);
        
        // RAG搜索工具
        RagSearchTool ragSearchTool = new RagSearchTool(ragService);
        toolInstances.put(ragSearchTool.getName(), ragSearchTool);
        
        log.info("写作工具管理器初始化完成，共加载 {} 个工具", toolInstances.size());
    }
    
    /**
     * 获取所有可用的工具回调
     */
    public List<ToolCallback> getAllToolCallbacks(String planId) {
        List<ToolCallback> callbacks = new ArrayList<>();
        
        for (WritingToolCallback tool : toolInstances.values()) {
            try {
                // 设置计划ID
                tool.setPlanId(planId);
                
                // 转换为Spring AI的ToolCallback
                ToolCallback callback = tool.toSpringAiToolCallback();
                callbacks.add(callback);
                
                log.debug("已加载工具: {}", tool.getName());
            } catch (Exception e) {
                log.error("加载工具 {} 失败: {}", tool.getName(), e.getMessage(), e);
            }
        }
        
        log.info("为计划 {} 加载了 {} 个工具回调", planId, callbacks.size());
        return callbacks;
    }
    
    /**
     * 获取指定名称的工具
     */
    public WritingToolCallback getTool(String toolName) {
        return toolInstances.get(toolName);
    }
    
    /**
     * 获取所有工具名称
     */
    public List<String> getAllToolNames() {
        return new ArrayList<>(toolInstances.keySet());
    }
    
    /**
     * 清理指定计划的工具资源
     */
    public void cleanupTools(String planId) {
        for (WritingToolCallback tool : toolInstances.values()) {
            try {
                tool.cleanup(planId);
            } catch (Exception e) {
                log.error("清理工具 {} 资源失败: {}", tool.getName(), e.getMessage(), e);
            }
        }
        log.info("已清理计划 {} 的工具资源", planId);
    }
    
    /**
     * 获取工具状态信息
     */
    public Map<String, String> getToolStates() {
        Map<String, String> states = new HashMap<>();
        for (WritingToolCallback tool : toolInstances.values()) {
            try {
                states.put(tool.getName(), tool.getCurrentToolState());
            } catch (Exception e) {
                states.put(tool.getName(), "获取状态失败: " + e.getMessage());
            }
        }
        return states;
    }
    
    /**
     * 检查工具是否可用
     */
    public boolean isToolAvailable(String toolName) {
        return toolInstances.containsKey(toolName);
    }
    
    /**
     * 获取工具描述信息
     */
    public Map<String, String> getToolDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        for (WritingToolCallback tool : toolInstances.values()) {
            descriptions.put(tool.getName(), tool.getDescription());
        }
        return descriptions;
    }
} 