package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Plot;

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
} 