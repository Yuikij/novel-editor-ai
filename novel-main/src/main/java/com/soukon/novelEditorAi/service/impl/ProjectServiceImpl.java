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
        if (project.getGenre() != null && !project.getGenre().isEmpty()) {
            sb.append("类型: ").append(project.getGenre()).append("\n");
        }
        if (project.getType() != null && !project.getType().isEmpty()) {
            sb.append("结构类型: ").append(project.getType()).append("\n");
        }
        if (project.getStyle() != null && !project.getStyle().isEmpty()) {
            sb.append("小说风格: ").append(project.getStyle()).append("\n");
        }
        if (project.getSynopsis() != null && !project.getSynopsis().isEmpty()) {
            sb.append("小说概要: ").append(project.getSynopsis()).append("\n");
        }
        if (project.getTags() != null && !project.getTags().isEmpty()) {
            sb.append("标签: ").append(String.join(", ", project.getTags())).append("\n");
        }
        if (project.getTargetAudience() != null && !project.getTargetAudience().isEmpty()) {
            sb.append("目标受众: ").append(project.getTargetAudience()).append("\n");
        }
        if (project.getWordCountGoal() != null) {
            sb.append("目标字数: ").append(project.getWordCountGoal()).append("\n");
        }
        if (project.getHighlights() != null && !project.getHighlights().isEmpty()) {
            sb.append("亮点: ").append(String.join(", ", project.getHighlights())).append("\n");
        }
        if (project.getWritingRequirements() != null && !project.getWritingRequirements().isEmpty()) {
            sb.append("写作要求: ").append(String.join(", ", project.getWritingRequirements())).append("\n");
        }
        if (project.getStatus() != null && !project.getStatus().isEmpty()) {
            sb.append("项目状态: ").append(project.getStatus()).append("\n");
        }
        if (project.getWorldId() != null) {
            sb.append("世界观ID: ").append(project.getWorldId()).append("\n");
        }
        return sb.toString();
    }
} 