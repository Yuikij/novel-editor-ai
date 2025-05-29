package com.soukon.novelEditorAi.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.service.PlotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 情节查询工具
 * 让LLM能够查询情节信息来辅助写作
 */
@Component
@Slf4j
public class PlotQueryTool {
    
    private final PlotService plotService;
    private String planId;
    private String lastQueryResult = "";
    
    public PlotQueryTool(PlotService plotService) {
        this.plotService = plotService;
    }
    
    @Tool(name = "plot_query", description = """
            查询小说中的情节信息，用于写作时获取情节的详细设定。
            可以根据情节标题、章节ID、项目ID或情节ID查询情节信息。
            返回情节的标题、描述、状态、完成度、目标字数等信息。
            """)
    public String queryPlot(
            Long projectId,
            Long chapterId,
            String plotTitle,
            Long plotId,
            String queryType) {
        
        try {
            log.info("情节查询工具调用: queryType={}, projectId={}, chapterId={}, plotTitle={}, plotId={}", 
                    queryType, projectId, chapterId, plotTitle, plotId);
            
            List<Plot> plots;
            
            switch (queryType) {
                case "by_title":
                    plots = queryByTitle(plotTitle, projectId);
                    break;
                    
                case "by_id":
                    Plot plot = plotService.getById(plotId);
                    plots = plot != null ? List.of(plot) : List.of();
                    break;
                    
                case "by_chapter":
                    plots = queryByChapter(chapterId);
                    break;
                    
                case "by_project":
                    plots = queryByProject(projectId);
                    break;
                    
                default:
                    return "不支持的查询类型: " + queryType;
            }
            
            String result = formatPlotInfo(plots);
            lastQueryResult = result;
            
            log.info("情节查询结果: 找到 {} 个情节", plots.size());
            return result;
            
        } catch (Exception e) {
            log.error("情节查询工具执行失败", e);
            return "查询失败: " + e.getMessage();
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
    
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    public String getCurrentToolState() {
        return "最后查询结果: " + (lastQueryResult.length() > 100 ? 
                lastQueryResult.substring(0, 100) + "..." : lastQueryResult);
    }
    
    public void cleanup(String planId) {
        this.lastQueryResult = "";
    }
} 