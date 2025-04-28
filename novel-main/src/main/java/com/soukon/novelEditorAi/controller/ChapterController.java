package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContentResponse;
import com.soukon.novelEditorAi.service.ChapterContentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节控制器
 * 提供章节内容生成和管理相关接口
 */
@RestController
@RequestMapping("/chapters")
@Slf4j
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    private final ChapterContentService chapterContentService;

    @Autowired
    public ChapterController(ChapterContentService chapterContentService) {
        this.chapterContentService = chapterContentService;
    }

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

    /**
     * 生成章节内容
     * @param chapterId 章节ID
     * @param projectId 项目ID
     * @param regenerate 是否重新生成
     * @param minWords 最小字数
     * @param stylePrompt 风格提示
     * @return 生成的章节内容
     */
    @GetMapping("/generate")
    public Result<ChapterContentResponse> generateChapterContent(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "regenerate", required = false, defaultValue = "false") Boolean regenerate,
            @RequestParam(value = "minWords", required = false, defaultValue = "500") Integer minWords,
            @RequestParam(value = "stylePrompt", required = false) String stylePrompt) {
        
        log.info("生成章节内容，章节ID: {}, 项目ID: {}, 重新生成: {}", chapterId, projectId, regenerate);
        
        try {
            // 创建章节内容生成请求
            ChapterContentRequest request = new ChapterContentRequest();
            request.setChapterId(chapterId);
            request.setPrompt(stylePrompt);
            request.setMaxTokens(minWords * 2); // 大致估算token数
            
            // 生成章节内容
            ChapterContentResponse response = chapterContentService.generateChapterContent(request);
            
            // 保存生成的内容（可选）
            // chapterContentService.saveChapterContent(chapterId, response.getContent());
            
            return Result.success(response);
        } catch (Exception e) {
            log.error("生成章节内容失败: {}", e.getMessage(), e);
            return Result.error(500, "生成章节内容失败: " + e.getMessage());
        }
    }

    /**
     * 流式生成章节内容
     * @param chapterId 章节ID
     * @param projectId 项目ID
     * @param response HTTP响应对象
     * @return 流式响应
     */
    @GetMapping("/generate/stream")
    public Flux<String> generateChapterContentStream(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam("projectId") Long projectId,
            HttpServletResponse response) {
        
        response.setCharacterEncoding("UTF-8");
        
        log.info("流式生成章节内容，章节ID: {}, 项目ID: {}", chapterId, projectId);
        
        // 创建章节内容生成请求
        ChapterContentRequest request = new ChapterContentRequest();
        request.setChapterId(chapterId);
        request.setStreamGeneration(true);
        
        // 这里简化处理，实际应用需要处理流式返回
        chapterContentService.generateChapterContentStream(request);
        
        return Flux.just("正在生成章节内容，请稍候...");
    }

    /**
     * 保存章节内容
     * @param chapterId 章节ID
     * @param content 章节内容
     * @return 保存结果
     */
    @PostMapping("/save")
    public Result<Boolean> saveChapterContent(
            @RequestParam("chapterId") Long chapterId,
            @RequestBody String content) {
        
        log.info("保存章节内容，章节ID: {}", chapterId);
        
        try {
            boolean success = chapterContentService.saveChapterContent(chapterId, content);
            if (success) {
                return Result.success(true);
            } else {
                return Result.error(404, "保存失败，未找到指定章节");
            }
        } catch (Exception e) {
            log.error("保存章节内容失败: {}", e.getMessage(), e);
            return Result.error(500, "保存章节内容失败: " + e.getMessage());
        }
    }
} 