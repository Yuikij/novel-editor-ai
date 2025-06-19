package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Plot;

import java.util.List;

public interface PlotService extends IService<Plot> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的单个情节信息。
     *
     * @param plot 情节实体
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    String toPrompt(Plot plot);

    String toCharacter(Plot plot);

    /**
     * 生成用于构建生成请求 Prompt 的情节信息部分。
     *
     * @param chapterId 章节 ID
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    String toPrompt(Long chapterId);
    
    /**
     * 根据已有的情节，补全或扩展情节列表到目标数量
     *
     * @param chapterId          章节ID
     * @param existingPlotIds    已有的情节ID列表
     * @param targetCount        目标情节总数
     * @return 补全后的情节列表（包含已有的和新生成的）
     */
    List<Plot> expandPlots(Long chapterId, List<Long> existingPlotIds, Integer targetCount);
    
    /**
     * 获取章节中第一个未完成的情节（完成度不是100%）
     *
     * @param chapterId 章节ID
     * @return 第一个未完成的情节，如果所有情节都已完成或没有情节则返回null
     */
    Plot getFirstIncompletePlot(Long chapterId);
    
    /**
     * 获取指定情节的上一个情节
     *
     * @param currentPlot 当前情节
     * @return 上一个情节，如果没有则返回null
     */
    Plot getPreviousPlot(Plot currentPlot);
    
    /**
     * 验证并处理情节的sortOrder，确保在同一章节中不重复
     * 如果发生重复，会自动调整后续情节的sortOrder
     *
     * @param plot 待验证的情节
     * @param isUpdate 是否为更新操作（true）还是新增操作（false）
     * @throws IllegalArgumentException 如果参数无效
     */
    void validateAndHandleSortOrder(Plot plot, boolean isUpdate);
} 