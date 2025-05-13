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
import org.springframework.beans.factory.annotation.Value;
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


    @Value("${novel.chapter.default-words-count:5000}")
    private Integer defaultWordsCount;

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
     * 批量删除章节
     *
     * @param ids 章节ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("IDs list cannot be empty");
        }

        chapterService.removeByIds(ids);
        return Result.success("批量删除成功", null);
    }

    /**
     * 自动补全或扩展章节列表到目标数量
     *
     * @param projectId   项目ID
     * @param targetCount 目标章节总数
     * @return 补全后的章节列表
     */
    @PostMapping("/auto-expand/{projectId}")
    public Result<List<Chapter>> autoExpandChapters(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "targetCount", required = false, defaultValue = "12") Integer targetCount) {
        log.info("自动扩展章节，项目ID: {}, 目标数量: {}", projectId, targetCount);
        try {
            // 获取项目所有现有章节ID
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            queryWrapper.select(Chapter::getId);
            List<Long> existingIds = this.chapterService.list(queryWrapper)
                    .stream()
                    .map(Chapter::getId)
                    .toList();

            // 调用扩展方法
            List<Chapter> expandedChapters = chapterService.expandChapters(
                    projectId, existingIds, targetCount);
            return Result.success("章节扩展成功", expandedChapters);
        } catch (Exception e) {
            log.error("章节扩展失败: {}", e.getMessage(), e);
            return Result.error("章节扩展失败: " + e.getMessage());
        }
    }

    /**
     * 生成章节内容
     *
     * @param chapterId           章节ID
     * @param projectId           项目ID
     * @param regenerate          是否重新生成
     * @param minWords            最小字数
     * @param stylePrompt         风格提示
     * @param appendMode          是否追加模式（true: 追加到已有内容后; false: 覆盖原有内容）
     * @param promptSuggestion    提示建议
     * @param wordCountSuggestion 字数建议
     * @return 生成的章节内容
     */
    @GetMapping("/generate")
    public Result<ChapterContentResponse> generateChapterContent(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "regenerate", required = false, defaultValue = "false") Boolean regenerate,
            @RequestParam(value = "minWords", required = false, defaultValue = "500") Integer minWords,
            @RequestParam(value = "stylePrompt", required = false) String stylePrompt,
            @RequestParam(value = "appendMode", required = false, defaultValue = "true") Boolean appendMode,
            @RequestParam(value = "promptSuggestion", required = false) String promptSuggestion,
            @RequestParam(value = "wordCountSuggestion", required = false) Integer wordCountSuggestion) {
        log.info("生成章节内容，章节ID: {}, 项目ID: {}, 重新生成: {}, 追加模式: {}",
                chapterId, projectId, regenerate, appendMode);
        try {
            // 创建章节内容生成请求
            ChapterContentRequest request = new ChapterContentRequest();
            request.setChapterId(chapterId);
            request.setPrompt(stylePrompt);
            request.setMaxTokens(minWords * 2); // 大致估算token数
            request.setAppendMode(appendMode);
            request.setPromptSuggestion(promptSuggestion);
            request.setWordCountSuggestion(wordCountSuggestion);
            // 生成章节内容
            ChapterContentResponse response = chapterContentService.generateChapterContent(request);
            // 保存生成的内容
            chapterContentService.saveChapterContent(chapterId, response.getContent(), appendMode);
            return Result.success(response);
        } catch (Exception e) {
            log.error("生成章节内容失败: {}", e.getMessage(), e);
            return Result.error(500, "生成章节内容失败: " + e.getMessage());
        }
    }

    /**
     * 流式生成章节内容
     *
     * @param chapterId           章节ID
     * @param projectId           项目ID
     * @param response            HTTP响应对象
     * @param promptSuggestion    提示建议
     * @param wordCountSuggestion 字数建议
     * @return 流式响应
     */
    @GetMapping("/generate/stream")
    public Flux<String> generateChapterContentStream(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam("projectId") Long projectId,
            HttpServletResponse response,
            @RequestParam(value = "promptSuggestion", required = false) String promptSuggestion,
            @RequestParam(value = "wordCountSuggestion", required = false) Integer wordCountSuggestion) {
        response.setCharacterEncoding("UTF-8");
        log.info("流式生成章节内容，章节ID: {}, 项目ID: {}", chapterId, projectId);
        ChapterContentRequest request = new ChapterContentRequest();
        request.setChapterId(chapterId);
        request.setStreamGeneration(true);
        request.setPromptSuggestion(promptSuggestion == null ? "无" : promptSuggestion);
        request.setWordCountSuggestion(wordCountSuggestion == null ? defaultWordsCount : wordCountSuggestion);
        return chapterContentService.generateChapterContentStreamFlux(request);
    }

    //    创建计划
    @GetMapping("/generate/execute")
    public Result<String> generateChapterContentExecute(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam("projectId") Long projectId,
            HttpServletResponse response,
            @RequestParam(value = "promptSuggestion", required = false) String promptSuggestion,
            @RequestParam(value = "wordCountSuggestion", required = false) Integer wordCountSuggestion) {
        response.setCharacterEncoding("UTF-8");
        log.info("创建章节生成计划，章节ID: {}, 项目ID: {}", chapterId, projectId);
        ChapterContentRequest request = new ChapterContentRequest();
        request.setChapterId(chapterId);
        request.setStreamGeneration(true);
        request.setPromptSuggestion(promptSuggestion == null ? "无" : promptSuggestion);
        request.setWordCountSuggestion(wordCountSuggestion == null ? defaultWordsCount : wordCountSuggestion);
        return chapterContentService.generateChapterContentExecute(request);
    }

    //    查询计划进度
    @GetMapping("/generate/progress")
    public Result<String> getGenerateProgress(@RequestParam("planId") String planId) {
        log.info("查询章节生成进度，计划ID: {}", planId);
//        chapterContentService.getGenerateProgress(planId);
//        String progress = chapterContentService.getGenerateProgress(chapterId);
        return Result.success("");
    }

    //    查询文章内容
    @GetMapping("/generate/content")
    public Flux<String> getGenerateContent(@RequestParam("planId") String planId) {
        return null;
    }

    /**
     * 保存章节内容
     *
     * @param chapterId  章节ID
     * @param content    章节内容
     * @param appendMode 是否为追加模式（true: 追加到已有内容后; false: 覆盖原有内容）
     * @return 保存结果
     */
    @PostMapping("/save")
    public Result<Boolean> saveChapterContent(
            @RequestParam("chapterId") Long chapterId,
            @RequestParam(value = "appendMode", required = false, defaultValue = "false") Boolean appendMode,
            @RequestBody String content) {

        log.info("保存章节内容，章节ID: {}, 追加模式: {}", chapterId, appendMode);

        try {
            boolean success = chapterContentService.saveChapterContent(chapterId, content, appendMode);
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