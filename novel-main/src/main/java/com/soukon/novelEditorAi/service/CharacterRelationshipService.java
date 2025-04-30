package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.CharacterRelationship;

import java.util.List;

public interface CharacterRelationshipService extends IService<CharacterRelationship> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed

    /**
     * 生成用于构建生成请求 Prompt 的角色关系信息。
     *
     * @param relationship 角色关系实体
     * @return 包含角色关系描述的字符串。
     */
    String toPrompt(CharacterRelationship relationship);

    /**
     * 根据项目ID获取角色关系列表
     */
    List<CharacterRelationship> getByProjectId(Long projectId);
} 