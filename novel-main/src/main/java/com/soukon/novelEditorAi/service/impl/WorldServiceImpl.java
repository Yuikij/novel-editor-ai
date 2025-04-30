package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.mapper.WorldMapper;
import com.soukon.novelEditorAi.service.WorldService;
import org.springframework.stereotype.Service;

@Service
public class WorldServiceImpl extends ServiceImpl<WorldMapper, World> implements WorldService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的世界观信息部分。
     *
     * @param world 世界观实体
     * @return 包含世界观名称和描述的字符串。
     */
    @Override
    public String toPrompt(World world) {
        if (world == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (world.getName() != null && !world.getName().isEmpty()) {
            sb.append("世界观名称: ").append(world.getName()).append("\n");
        }
        if (world.getDescription() != null && !world.getDescription().isEmpty()) {
            sb.append("世界观描述: ").append(world.getDescription()).append("\n");
        }
        if (world.getElements() != null && !world.getElements().isEmpty()) {
            sb.append("关键元素:\n");
            for (com.soukon.novelEditorAi.entities.Element element : world.getElements()) {
                sb.append("- ");
                if (element.getType() != null && !element.getType().isEmpty()) {
                    sb.append("[").append(element.getType()).append("] ");
                }
                if (element.getName() != null && !element.getName().isEmpty()) {
                    sb.append(element.getName());
                }
                if (element.getDescription() != null && !element.getDescription().isEmpty()) {
                    sb.append(": ").append(element.getDescription());
                }
                if (element.getDetails() != null && !element.getDetails().isEmpty()) {
                    sb.append(" (详情: ").append(element.getDetails()).append(")");
                }
                sb.append("\n");
            }
        }
        if (world.getNotes() != null && !world.getNotes().isEmpty()) {
            sb.append("备注: ").append(world.getNotes()).append("\n");
        }
        return sb.toString();
    }
} 