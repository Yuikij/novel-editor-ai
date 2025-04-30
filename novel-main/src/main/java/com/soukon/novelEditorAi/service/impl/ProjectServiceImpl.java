package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.mapper.ProjectMapper;
import com.soukon.novelEditorAi.service.ProjectService;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的项目信息部分。
     *
     * @param project 项目实体
     * @return 包含小说标题、概要和风格的字符串。
     */
    @Override
    public String toPrompt(Project project) {
        if (project == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        if (project.getTitle() != null && !project.getTitle().isEmpty()) {
            sb.append("小说标题: ").append(project.getTitle()).append("\n");
        }
        if (project.getSynopsis() != null && !project.getSynopsis().isEmpty()) {
            sb.append("小说概要: ").append(project.getSynopsis()).append("\n");
        }
        if (project.getStyle() != null && !project.getStyle().isEmpty()) {
            sb.append("小说风格: ").append(project.getStyle()).append("\n");
        }
        // 可以根据需要添加更多字段，例如 genre, targetAudience 等
        return sb.toString();
    }
} 