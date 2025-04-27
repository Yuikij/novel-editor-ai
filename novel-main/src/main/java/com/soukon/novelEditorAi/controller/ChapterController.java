package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.service.ChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/chapters")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    @GetMapping
    public Result<List<Chapter>> list() {
        List<Chapter> chapters = chapterService.list();
        return Result.success(chapters);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<Chapter>> listByProjectId(@PathVariable("projectId") Long projectId) {
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Chapter::getProjectId, projectId);
        queryWrapper.orderByAsc(Chapter::getSortOrder);
        List<Chapter> chapters = chapterService.list(queryWrapper);
        return Result.success(chapters);
    }

    @GetMapping("/page")
    public Result<Page<Chapter>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "projectId", required = false, name = "projectId") Long projectId,
            @RequestParam(value = "title", required = false, name = "title") String title,
            @RequestParam(value = "status", required = false, name = "status") String status) {
        
        Page<Chapter> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
        
        if (projectId != null) {
            queryWrapper.eq(Chapter::getProjectId, projectId);
        }
        if (title != null && !title.isEmpty()) {
            queryWrapper.like(Chapter::getTitle, title);
        }
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq(Chapter::getStatus, status);
        }
        
        queryWrapper.orderByAsc(Chapter::getProjectId).orderByAsc(Chapter::getSortOrder);
        chapterService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<Chapter> getById(@PathVariable("id") Long id) {
        Chapter chapter = chapterService.getById(id);
        if (chapter != null) {
            return Result.success(chapter);
        }
        return Result.error("Chapter not found with id: " + id);
    }

    @PostMapping
    public Result<Chapter> save(@RequestBody Chapter chapter) {
        LocalDateTime now = LocalDateTime.now();
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        
        // If order is not set, find the max order in the project and set to order+1
        if (chapter.getSortOrder() == null) {
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, chapter.getProjectId());
            queryWrapper.orderByDesc(Chapter::getSortOrder);
            queryWrapper.last("LIMIT 1");
            Chapter lastChapter = chapterService.getOne(queryWrapper);
            
            if (lastChapter != null) {
                chapter.setSortOrder(lastChapter.getSortOrder() + 1);
            } else {
                chapter.setSortOrder(1);
            }
        }
        
        chapterService.save(chapter);
        return Result.success("Chapter created successfully", chapter);
    }

    @PutMapping("/{id}")
    public Result<Chapter> update(@PathVariable("id") Long id, @RequestBody Chapter chapter) {
        Chapter existingChapter = chapterService.getById(id);
        if (existingChapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }
        
        chapter.setId(id);
        chapter.setCreatedAt(existingChapter.getCreatedAt());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterService.updateById(chapter);
        
        return Result.success("Chapter updated successfully", chapter);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }
        
        chapterService.removeById(id);
        return Result.success("Chapter deleted successfully", null);
    }
} 