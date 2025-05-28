package com.soukon.novelEditorAi.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.service.PlotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情节查询工具
 * 让LLM能够查询情节信息来辅助写作
 */
@Slf4j
public class PlotQueryTool implements WritingToolCallback {
    
    private static final String TOOL_NAME = "plot_query";
    private static final String DESCRIPTION = """
            查询小说中的情节信息，用于写作时获取情节的详细设定。
            可以根据情节标题、章节ID、项目ID或情节ID查询情节信息。
            返回情节的标题、描述、状态、完成度、目标字数等信息。
            """;
    
    private static final String PARAMETERS = """
            {
                "type": "object",
                "properties": {
                    "projectId": {
                        "type": "integer",
                        "description": "项目ID，用于限定查询范围"
                    },
                    "chapterId": {
                        "type": "integer",
                        "description": "章节ID，查询特定章节的情节"
                    },
                    "plotTitle": {
                        "type": "string",
                        "description": "情节标题，支持模糊匹配"
                    },
                    "plotId": {
                        "type": "integer",
                        "description": "情节ID，精确查询特定情节"
                    },
                    "queryType": {
                        "type": "string",
                        "enum": ["by_title", "by_id", "by_chapter", "by_project"],
                        "description": "查询类型：by_title(按标题), by_id(按ID), by_chapter(按章节), by_project(按项目)"
                    }
                },
                "required": ["queryType"]
            }
            """;
    
    private final PlotService plotService;
    private String planId;
    private String lastQueryResult = "";
    
    public PlotQueryTool(PlotService plotService) {
        this.plotService = plotService;
    }
    
    @Override
    public WritingToolResult apply(String input, ToolContext toolContext) {
        try {
            log.info("情节查询工具输入: {}", input);
            
            Map<String, Object> params = JSON.parseObject(input, new TypeReference<Map<String, Object>>() {});
            String queryType = (String) params.get("queryType");
            
            List<Plot> plots;
            
            switch (queryType) {
                case "by_title":
                    String plotTitle = (String) params.get("plotTitle");
                    Long projectId = params.get("projectId") != null ? 
                            Long.valueOf(params.get("projectId").toString()) : null;
                    plots = queryByTitle(plotTitle, projectId);
                    break;
                    
                case "by_id":
                    Long plotId = Long.valueOf(params.get("plotId").toString());
                    Plot plot = plotService.getById(plotId);
                    plots = plot != null ? List.of(plot) : List.of();
                    break;
                    
                case "by_chapter":
                    Long chapterId = Long.valueOf(params.get("chapterId").toString());
                    plots = queryByChapter(chapterId);
                    break;
                    
                case "by_project":
                    Long projId = Long.valueOf(params.get("projectId").toString());
                    plots = queryByProject(projId);
                    break;
                    
                default:
                    return WritingToolResult.failure("不支持的查询类型: " + queryType);
            }
            
            String result = formatPlotInfo(plots);
            lastQueryResult = result;
            
            log.info("情节查询结果: 找到 {} 个情节", plots.size());
            return WritingToolResult.success(result);
            
        } catch (Exception e) {
            log.error("情节查询工具执行失败", e);
            return WritingToolResult.failure("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 按标题查询情节
     */
    private List<Plot> queryByTitle(String plotTitle, Long projectId) {
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            queryWrapper.eq(Plot::getProjectId, projectId);
        }
        if (plotTitle != null && !plotTitle.trim().isEmpty()) {
            queryWrapper.like(Plot::getTitle, plotTitle);
        }
        return plotService.list(queryWrapper);
    }
    
    /**
     * 按章节查询情节
     */
    private List<Plot> queryByChapter(Long chapterId) {
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Plot::getChapterId, chapterId);
        queryWrapper.orderByAsc(Plot::getSortOrder);
        return plotService.list(queryWrapper);
    }
    
    /**
     * 按项目查询情节
     */
    private List<Plot> queryByProject(Long projectId) {
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Plot::getProjectId, projectId);
        queryWrapper.orderByAsc(Plot::getSortOrder);
        return plotService.list(queryWrapper);
    }
    
    /**
     * 格式化情节信息
     */
    private String formatPlotInfo(List<Plot> plots) {
        if (plots.isEmpty()) {
            return "未找到匹配的情节信息。";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== 情节信息查询结果 ===\n\n");
        
        for (Plot plot : plots) {
            result.append("**情节标题**: ").append(plot.getTitle()).append("\n");
            
            if (plot.getDescription() != null && !plot.getDescription().trim().isEmpty()) {
                result.append("**情节描述**: ").append(plot.getDescription()).append("\n");
            }
            
            if (plot.getType() != null) {
                result.append("**情节类型**: ").append(plot.getType()).append("\n");
            }
            
            if (plot.getStatus() != null) {
                result.append("**状态**: ").append(plot.getStatus()).append("\n");
            }
            
            if (plot.getCompletionPercentage() != null) {
                result.append("**完成度**: ").append(plot.getCompletionPercentage()).append("%\n");
            }
            
            if (plot.getWordCountGoal() != null) {
                result.append("**目标字数**: ").append(plot.getWordCountGoal()).append("字\n");
            }
            
            if (plot.getSortOrder() != null) {
                result.append("**排序**: ").append(plot.getSortOrder()).append("\n");
            }
            
            if (plot.getCharacterIds() != null && !plot.getCharacterIds().isEmpty()) {
                result.append("**涉及角色ID**: ").append(plot.getCharacterIds().toString()).append("\n");
            }
            
            if (plot.getItemIds() != null && !plot.getItemIds().isEmpty()) {
                result.append("**相关条目ID**: ").append(plot.getItemIds().toString()).append("\n");
            }
            
            result.append("\n---\n\n");
        }
        
        return result.toString();
    }
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
    
    @Override
    public String getParameters() {
        return PARAMETERS;
    }
    
    @Override
    public Class<?> getInputType() {
        return String.class;
    }
    
    @Override
    public boolean isReturnDirect() {
        return false;
    }
    
    @Override
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    @Override
    public String getCurrentToolState() {
        return "最后查询结果: " + (lastQueryResult.length() > 100 ? 
                lastQueryResult.substring(0, 100) + "..." : lastQueryResult);
    }
    
    @Override
    public void cleanup(String planId) {
        this.lastQueryResult = "";
    }
    
    @Override
    public ToolCallback toSpringAiToolCallback() {
        return FunctionToolCallback.builder(TOOL_NAME, this)
                .description(DESCRIPTION)
                .inputSchema(PARAMETERS)
                .inputType(String.class)
                .build();
    }
} 