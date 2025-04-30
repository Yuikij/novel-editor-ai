package com.soukon.novelEditorAi.service;

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
} 