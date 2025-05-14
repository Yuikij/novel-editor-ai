package com.soukon.novelEditorAi.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Project;

public interface ProjectService extends IService<Project> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的项目信息部分。
     *
     * @param project 项目实体
     * @return 包含小说标题、概要和风格的字符串。
     */
    String toPrompt(Project project);


    /**
     * 生成用于构建生成请求 Prompt 的项目 ID 信息部分。
     *
     * @param projectId 项目 ID
     * @return 包含项目 ID 的字符串。
     */
    String toPrompt(Long projectId);
    
    /**
     * 保存项目草稿
     *
     * @param projectId 项目ID
     * @param draft 草稿JSON数据
     * @return 更新后的项目实体
     */
    Project saveDraft(Long projectId, JSONObject draft);
    
    /**
     * 获取项目草稿
     *
     * @param projectId 项目ID
     * @return 项目草稿JSON数据，如果不存在则返回空JSONObject
     */
    JSONObject getDraft(Long projectId);
} 