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
import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

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
        projectService.save(project);
        return Result.success("Project created successfully", project);
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable("id") Long id, @RequestBody Project project) {
        Project existingProject = projectService.getById(id);
        if (existingProject == null) {
            return Result.error("Project not found with id: " + id);
        }
        
        project.setId(id);
        project.setCreatedAt(existingProject.getCreatedAt());
        project.setUpdatedAt(LocalDateTime.now());
        projectService.updateById(project);
        
        return Result.success("Project updated successfully", project);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return Result.error("Project not found with id: " + id);
        }
        
        projectService.removeById(id);
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
} 