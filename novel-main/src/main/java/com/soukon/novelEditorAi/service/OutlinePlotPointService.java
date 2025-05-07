package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;

import java.util.List;
import java.util.Map;

public interface OutlinePlotPointService extends IService<OutlinePlotPoint> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed

    /**
     * 生成用于构建生成请求 Prompt 的大纲情节点信息。
     *
     * @param point 大纲情节点实体
     * @return 包含情节点描述的字符串。
     */
    String toPrompt(OutlinePlotPoint point);


    /**
     * 生成用于构建生成请求 Prompt 的大纲情节点信息。
     *
     * @param projectId 项目ID
     * @return 包含情节点描述的字符串。
     */
    String toPrompt(Long projectId);


    /**
     * 根据已有的大纲情节点，补全或扩展情节点列表
     *
     * @param projectId 项目ID
     * @param existingPlotPointIds 已有的情节点ID列表
     * @param targetCount 目标情节点总数
     * @return 补全后的情节点列表（包含已有的和新生成的）
     */
    List<OutlinePlotPoint> expandOutlinePlotPoints(Long projectId, List<Long> existingPlotPointIds, Integer targetCount);
} 