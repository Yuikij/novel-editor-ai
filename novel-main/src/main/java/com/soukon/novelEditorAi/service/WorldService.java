package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.World;

public interface WorldService extends IService<World> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的世界观信息部分。
     *
     * @param world 世界观实体
     * @return 包含世界观名称和描述的字符串。
     */
    String toPrompt(World world);


    /**
     * 生成用于构建生成请求 Prompt 的世界观信息部分。
     *
     * @param worldId 世界观 ID
     * @return 包含世界观名称和描述的字符串。
     */
    String toPrompt(Long worldId);

} 