package com.soukon.novelEditorAi.controller;

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
} 