package com.soukon.novelEditorAi.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@RestController
@RequestMapping("/projects")
@Slf4j
public class ProjectController {

    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private com.soukon.novelEditorAi.service.EntitySyncHelper entitySyncHelper;
    
    @Autowired
    private com.soukon.novelEditorAi.service.UnifiedVectorSyncService unifiedVectorSyncService;

    @GetMapping
    public Result<List<Project>> list() {
        List<Project> projects = projectService.list();
        return Result.success(projects);
    }

    @GetMapping("/page")
    public Result<Page<Project>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "title", required = false, name = "title") String title,
            @RequestParam(value = "genre", required = false, name = "genre") String genre,
            @RequestParam(value = "status", required = false, name = "status") String status) {
        
        Page<Project> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<Project> queryWrapper = new LambdaQueryWrapper<>();
        
        if (title != null && !title.isEmpty()) {
            queryWrapper.like(Project::getTitle, title);
        }
        if (genre != null && !genre.isEmpty()) {
            queryWrapper.eq(Project::getGenre, genre);
        }
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq(Project::getStatus, status);
        }
        
        queryWrapper.orderByDesc(Project::getUpdatedAt);
        projectService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable("id") Long id) {
        Project project = projectService.getById(id);
        if (project != null) {
            return Result.success(project);
        }
        return Result.error("Project not found with id: " + id);
    }

    @PostMapping
    public Result<Project> save(@RequestBody Project project) {
        LocalDateTime now = LocalDateTime.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        
        // 初始化向量版本号
        if (project.getVectorVersion() == null) {
            project.setVectorVersion(1L);
        }
        
        projectService.save(project);
        
        // 触发向量同步（异步）
        entitySyncHelper.triggerCreate("project", project.getId(), 
            project.getId(), false);
        
        return Result.success("Project created successfully", project);
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable("id") Long id, @RequestBody Project project) {
        Project existingProject = projectService.getById(id);
        if (existingProject == null) {
            return Result.error("Project not found with id: " + id);
        }
        
        // 检查世界观关联是否发生变化
        Long oldWorldId = existingProject.getWorldId();
        Long newWorldId = project.getWorldId();
        boolean worldChanged = !Objects.equals(oldWorldId, newWorldId);
        
        project.setId(id);
        project.setCreatedAt(existingProject.getCreatedAt());
        project.setUpdatedAt(LocalDateTime.now());
        
        // 递增向量版本号
        Long currentVersion = existingProject.getVectorVersion();
        project.setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
        
        // 检查内容是否发生变更
        String oldContent = buildProjectContent(existingProject);
        String newContent = buildProjectContent(project);
        boolean contentChanged = entitySyncHelper.isContentChanged(oldContent, newContent);
        
        projectService.updateById(project);
        
        // 处理世界观关联变化
        if (worldChanged) {
            // 删除旧的世界观关联文档
            if (oldWorldId != null) {
                try {
                    unifiedVectorSyncService.deleteWorldProjectDocument(oldWorldId, id);
                } catch (Exception e) {
                    log.warn("删除旧世界观关联文档失败: worldId={}, projectId={}", oldWorldId, id, e);
                }
            }
            
            // 新的世界观关联会在项目向量同步时自动创建
        }
        
        // 只在内容真正变更时才触发向量同步
        if (contentChanged || worldChanged) {
            entitySyncHelper.triggerUpdate("project", project.getId(), 
                project.getVectorVersion(), project.getId(), false);
        }
        
        return Result.success("Project updated successfully", project);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return Result.error("Project not found with id: " + id);
        }
        
        // 删除世界观关联文档（如果存在）
        if (project.getWorldId() != null) {
            try {
                unifiedVectorSyncService.deleteWorldProjectDocument(project.getWorldId(), id);
            } catch (Exception e) {
                log.warn("删除世界观关联文档失败: worldId={}, projectId={}", project.getWorldId(), id, e);
            }
        }
        
        projectService.removeById(id);
        
        // 触发向量同步删除（紧急处理）
        entitySyncHelper.triggerDelete("project", id, id, true);
        
        return Result.success("Project deleted successfully", null);
    }

    /**
     * 保存项目草稿
     * @param id 项目ID
     * @param draftJson 草稿JSON数据
     * @return 更新结果
     */
    @PostMapping("/{id}/draft")
    public Result<Project> saveDraft(@PathVariable("id") Long id, @RequestBody JSONObject draftJson) {
        try {
            Project updatedProject = projectService.saveDraft(id, draftJson);
            return Result.success("Draft saved successfully", updatedProject);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("Failed to save draft: " + e.getMessage());
        }
    }
    
    /**
     * 获取项目草稿
     * @param id 项目ID
     * @return 项目草稿JSON数据
     */
    @GetMapping("/{id}/draft")
    public Result<JSONObject> getDraft(@PathVariable("id") Long id) {
        try {
            JSONObject draft = projectService.getDraft(id);
            return Result.success(draft);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("Failed to retrieve draft: " + e.getMessage());
        }
    }


    /**
     * 保存项目地图
     * @param id 项目ID
     * @param mapJson 草稿JSON数据
     * @return 更新结果
     */
    @PostMapping("/{id}/map")
    public Result<Project> saveMap(@PathVariable("id") Long id, @RequestBody JSONObject mapJson) {
        try {
            Project updatedProject = projectService.saveMap(id, mapJson);
            return Result.success("map saved successfully", updatedProject);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("Failed to save map: " + e.getMessage());
        }
    }

    /**
     * 获取项目地图
     * @param id 项目ID
     * @return 项目地图SON数据
     */
    @GetMapping("/{id}/map")
    public Result<JSONObject> getMap(@PathVariable("id") Long id) {
        try {
            JSONObject map = projectService.getMap(id);
            return Result.success(map);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("Failed to retrieve map: " + e.getMessage());
        }
    }
    
    /**
     * 构建项目内容用于向量化比较
     */
    private String buildProjectContent(Project project) {
        if (project == null) return "";
        
        StringBuilder content = new StringBuilder();
        if (project.getTitle() != null) {
            content.append("标题: ").append(project.getTitle()).append("\n");
        }
        if (project.getSynopsis() != null) {
            content.append("简介: ").append(project.getSynopsis()).append("\n");
        }
        if (project.getGenre() != null) {
            content.append("类型: ").append(project.getGenre()).append("\n");
        }
        if (project.getStyle() != null) {
            content.append("风格: ").append(project.getStyle()).append("\n");
        }
        if (project.getTargetAudience() != null) {
            content.append("目标受众: ").append(project.getTargetAudience()).append("\n");
        }
        if (project.getTags() != null && !project.getTags().isEmpty()) {
            content.append("标签: ").append(String.join(", ", project.getTags())).append("\n");
        }
        if (project.getHighlights() != null && !project.getHighlights().isEmpty()) {
            content.append("亮点: ").append(String.join(", ", project.getHighlights())).append("\n");
        }
        if (project.getWritingRequirements() != null && !project.getWritingRequirements().isEmpty()) {
            content.append("写作要求: ").append(String.join(", ", project.getWritingRequirements())).append("\n");
        }
        
        return content.toString();
    }
} 