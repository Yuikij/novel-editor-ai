package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;

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
} 