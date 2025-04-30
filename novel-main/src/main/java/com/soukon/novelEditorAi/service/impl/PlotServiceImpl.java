package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.service.PlotService;
import org.springframework.stereotype.Service;

@Service
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的单个情节信息。
     *
     * @param plot 情节实体
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    @Override
    public String toPrompt(Plot plot) {
        if (plot == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        if (plot.getDescription() != null && !plot.getDescription().isEmpty()) {
            sb.append("- ").append(plot.getDescription()).append("\n");
        }
        // 可以根据需要添加 title 或 type 等信息
        return sb.toString();
    }
} 