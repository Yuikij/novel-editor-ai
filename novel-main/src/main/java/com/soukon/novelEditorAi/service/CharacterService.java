package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Character;

public interface CharacterService extends IService<Character> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的单个角色信息。
     *
     * @param character 角色实体
     * @return 包含角色姓名和描述的字符串。
     */
    String toPrompt(Character character);

    /**
     * 生成用于构建生成请求 Prompt 的角色列表信息。
     *
     * @param projectId 项目 ID
     * @return 包含所有角色姓名和描述的字符串。
     */
     String toPrompt(Long projectId);
} 