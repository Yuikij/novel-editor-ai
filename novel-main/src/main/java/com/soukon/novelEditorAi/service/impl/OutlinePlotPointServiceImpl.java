package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;
import com.soukon.novelEditorAi.mapper.OutlinePlotPointMapper;
import com.soukon.novelEditorAi.service.OutlinePlotPointService;
import org.springframework.stereotype.Service;

@Service
public class OutlinePlotPointServiceImpl extends ServiceImpl<OutlinePlotPointMapper, OutlinePlotPoint> implements OutlinePlotPointService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed

    /**
     * 生成用于构建生成请求 Prompt 的大纲情节点信息。
     *
     * @param point 大纲情节点实体
     * @return 包含情节点描述的字符串。
     */
    @Override
    public String toPrompt(OutlinePlotPoint point) {
        if (point == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (point.getTitle() != null && !point.getTitle().isEmpty()) {
            sb.append("标题: ").append(point.getTitle());
        }
        if (point.getType() != null && !point.getType().isEmpty()) {
            sb.append(" [类型: ").append(point.getType()).append("]");
        }
        if (point.getDescription() != null && !point.getDescription().isEmpty()) {
            sb.append(" 描述: ").append(point.getDescription());
        }
        sb.append("\n");
        return sb.toString();
    }
} 