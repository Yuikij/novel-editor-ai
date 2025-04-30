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
        if (world.getName() != null && !world.getName().isEmpty() && 
            world.getDescription() != null && !world.getDescription().isEmpty()) {
            sb.append("世界观:\n");
            sb.append(world.getName()).append(": ").append(world.getDescription()).append("\n");
        }
        // 可以在这里添加对 elements 的处理逻辑，如果需要的话
        // 例如：
        // if (world.getElements() != null && !world.getElements().isEmpty()) {
        //     sb.append("关键元素:\n");
        //     for (Element element : world.getElements()) {
        //         sb.append("- ").append(element.getName()).append(": ").append(element.getDescription()).append("\n");
        //     }
        // }
        return sb.toString();
    }
} 