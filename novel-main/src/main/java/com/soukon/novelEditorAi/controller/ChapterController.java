package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.model.chapter.ChapterListDTO;
import com.soukon.novelEditorAi.service.ChapterService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanState;
import com.soukon.novelEditorAi.service.ChapterContentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import com.alibaba.fastjson.JSONObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;

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

    /**
     * 查询所有章节列表（不包含content和historyContent字段，推荐使用）
     * @return 章节列表
     */
    @GetMapping("/list")
    public Result<List<ChapterListDTO>> listChapters() {
        List<ChapterListDTO> chapters = chapterService.getAllChapterList();
        return Result.success(chapters);
    }

    /**
     * 查询所有章节（包含content和historyContent字段，不推荐在列表场景使用）
     * @return 章节列表
     * @deprecated 建议使用 /list 接口，避免返回大的content字段
     */
    @GetMapping
    @Deprecated
    public Result<List<Chapter>> list() {
        List<Chapter> chapters = chapterService.list();
        return Result.success(chapters);
    }

    /**
     * 根据项目ID查询章节列表（不包含content和historyContent字段，推荐使用）
     * @param projectId 项目ID
     * @return 章节列表
     */
    @GetMapping("/project/{projectId}/list")
    public Result<List<ChapterListDTO>> listChaptersByProjectId(@PathVariable("projectId") Long projectId) {
        List<ChapterListDTO> chapters = chapterService.getChapterListByProjectId(projectId);
        return Result.success(chapters);
    }

    /**
     * 根据项目ID查询章节（包含content和historyContent字段，不推荐）
     * @param projectId 项目ID
     * @return 章节列表
     * @deprecated 建议使用 /project/{projectId}/list 接口，避免返回大的content字段
     */
    @GetMapping("/project/{projectId}")
    @Deprecated
    public Result<List<Chapter>> listByProjectId(@PathVariable("projectId") Long projectId) {
        LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Chapter::getProjectId, projectId);
        queryWrapper.orderByAsc(Chapter::getSortOrder);
        List<Chapter> chapters = chapterService.list(queryWrapper);
        return Result.success(chapters);
    }

    /**
     * 分页查询章节列表（不包含content和historyContent字段，推荐使用）
     * @param page 页码
     * @param pageSize 每页大小
     * @param projectId 项目ID（可选）
     * @param title 章节标题（可选）
     * @param status 章节状态（可选）
     * @return 分页结果
     */
    @GetMapping("/list/page")
    public Result<Page<ChapterListDTO>> pageChapterList(
            @RequestParam(value = "page", defaultValue = "1") Integer page, 
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize, 
            @RequestParam(value = "projectId", required = false) Long projectId, 
            @RequestParam(value = "title", required = false) String title, 
            @RequestParam(value = "status", required = false) String status) {
        
        Page<ChapterListDTO> pageInfo = chapterService.pageChapterList(page, pageSize, projectId, title, status);
        return Result.success(pageInfo);
    }

    /**
     * 分页查询章节（包含content和historyContent字段，不推荐在列表场景使用）
     * @param page 页码
     * @param pageSize 每页大小
     * @param projectId 项目ID（可选）
     * @param title 章节标题（可选）
     * @param status 章节状态（可选）
     * @return 分页结果
     * @deprecated 建议使用 /list/page 接口，避免返回大的content字段
     */
    @GetMapping("/page")
    @Deprecated
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

    /**
     * 根据ID获取章节详情（包含完整的content和historyContent字段）
     * @param id 章节ID
     * @return 章节详情信息
     */
    @GetMapping("/{id}/detail")
    public Result<Chapter> getChapterDetail(@PathVariable("id") Long id) {
        Chapter chapter = chapterService.getById(id);
        if (chapter != null) {
            return Result.success(chapter);
        }
        return Result.error("Chapter not found with id: " + id);
    }

    /**
     * 根据ID获取章节基本信息（兼容旧接口，建议使用detail接口获取完整信息）
     * @param id 章节ID
     * @return 章节信息
     */
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
        try {
            LocalDateTime now = LocalDateTime.now();
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);

            // 验证并处理sortOrder重复问题
            chapterService.validateAndHandleSortOrder(chapter, false);

            chapterService.save(chapter);
            return Result.success("Chapter created successfully", chapter);
        } catch (IllegalArgumentException e) {
            log.error("章节创建参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("章节创建失败: {}", e.getMessage(), e);
            return Result.error("章节创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Chapter> update(@PathVariable("id") Long id, @RequestBody Chapter chapter) {
        try {
            Chapter existingChapter = chapterService.getById(id);
            if (existingChapter == null) {
                return Result.error("Chapter not found with id: " + id);
            }

            // 设置章节ID和项目ID（确保验证时有完整信息）
            chapter.setId(id);
            if (chapter.getProjectId() == null) {
                chapter.setProjectId(existingChapter.getProjectId());
            }

            // 验证并处理sortOrder重复问题
            chapterService.validateAndHandleSortOrder(chapter, true);

            // Save chapter history before updating

            // Initialize historyContent if it's null
            if (existingChapter.getHistoryContent() == null) {
                existingChapter.setHistoryContent(new JSONObject());
            }
            JSONObject historyContent = existingChapter.getHistoryContent();
            // Create a new history entry with timestamp as key
            String timestamp = String.valueOf(System.currentTimeMillis());
            historyContent.put(timestamp, chapter.getContent());
            // Keep only the most recent 10 entries
            if (historyContent.size() > 6) {
                // Find the oldest timestamp key
                String oldestKey = historyContent.keySet().stream()
                        .sorted()
                        .findFirst()
                        .orElse(null);

                // Remove the oldest entry
                historyContent.remove(oldestKey);
            }
            chapter.setHistoryContent(historyContent);
            chapter.setCreatedAt(existingChapter.getCreatedAt());
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterService.updateById(chapter);

            return Result.success("Chapter updated successfully", chapter);
        } catch (IllegalArgumentException e) {
            log.error("章节更新参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("章节更新失败: {}", e.getMessage(), e);
            return Result.error("章节更新失败: " + e.getMessage());
        }
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
     * 重新整理项目中所有章节的排序，确保连续且无重复
     *
     * @param projectId 项目ID
     * @return 重新整理的结果
     */
    @PostMapping("/reorder/{projectId}")
    public Result<Integer> reorderChaptersByProject(@PathVariable("projectId") Long projectId) {
        try {
            int reorderedCount = chapterService.reorderChaptersByProject(projectId);
            return Result.success("章节排序整理成功，更新了" + reorderedCount + "个章节", reorderedCount);
        } catch (IllegalArgumentException e) {
            log.error("重新整理章节排序参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("重新整理章节排序失败: {}", e.getMessage(), e);
            return Result.error("重新整理失败: " + e.getMessage());
        }
    }

    /**
     * 自动补全或扩展章节列表到目标数量
     *
     * @param projectId   项目ID
     * @param targetCount 目标章节总数
     * @return 补全后的章节列表
     */
    @PostMapping("/auto-expand/{projectId}")
    public Result<List<Chapter>> autoExpandChapters(@PathVariable("projectId") Long projectId, @RequestParam(value = "targetCount", required = false, defaultValue = "12") Integer targetCount) {
        log.info("自动扩展章节，项目ID: {}, 目标数量: {}", projectId, targetCount);
        try {
            // 获取项目所有现有章节ID
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            queryWrapper.select(Chapter::getId);
            List<Long> existingIds = this.chapterService.list(queryWrapper).stream().map(Chapter::getId).toList();

            // 调用扩展方法
            List<Chapter> expandedChapters = chapterService.expandChapters(projectId, existingIds, targetCount);
            return Result.success("章节扩展成功", expandedChapters);
        } catch (Exception e) {
            log.error("章节扩展失败: {}", e.getMessage(), e);
            return Result.error("章节扩展失败: " + e.getMessage());
        }
    }

    //    创建计划
    @GetMapping("/generate/execute")
    public Result<String> generateChapterContentExecute(@RequestParam("chapterId") Long chapterId, @RequestParam(value = "templateId", required = false) Long templateId,
                                                        @RequestParam("projectId") Long projectId, HttpServletResponse response,
                                                        @RequestParam(value = "promptSuggestion", required = false) String promptSuggestion,
                                                        @RequestParam(value = "freedom", required = false, defaultValue = "false") Boolean freedom,
                                                        @RequestParam(value = "wordCountSuggestion", required = false) Integer wordCountSuggestion) {
        response.setCharacterEncoding("UTF-8");
        log.info("创建章节生成计划，章节ID: {}, 项目ID: {}", chapterId, projectId);
        ChapterContentRequest request = new ChapterContentRequest();
        request.setTemplateId(templateId);
        request.setChapterId(chapterId);
        request.setFreedom(freedom);
        request.setPromptSuggestion(promptSuggestion == null ? "无" : promptSuggestion);
        request.setWordCountSuggestion(wordCountSuggestion);
        return chapterContentService.generateChapterContentExecute(request);
    }

    //    查询计划进度
    @GetMapping("/generate/progress")
    public Result<Object> getGenerateProgress(@RequestParam("planId") String planId) {
        log.info("查询章节生成进度，计划ID: {}", planId);

        // 从章节内容服务中获取计划上下文
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext == null) {
            return Result.error("计划不存在，请检查计划ID是否正确");
        }

        // 获取计划状态
        PlanState planState = planContext.getPlanState();
        Integer progressRate = planContext.getProgress();
        String message = planContext.getMessage();
        if (planState == null) {
            planState = PlanState.PLANNING; // 默认状态
        }

        // 构建进度信息
        HashMap<String, Object> progressInfo = new HashMap<>();
        progressInfo.put("planId", planId);
        progressInfo.put("state", planState.getCode());
        progressInfo.put("stateMessage", planState.getMessage());
        progressInfo.put("hasContent", planContext.getPlanStream() != null);
        progressInfo.put("progress", progressRate);
        progressInfo.put("message", message);

        return Result.success(planState.getMessage(), progressInfo);
    }

    //    查询文章内容
    @GetMapping("/generate/content")
    public Flux<String> getGenerateContent(@RequestParam("planId") String planId) {
        log.info("查询章节生成内容，计划ID: {}", planId);

        // 从章节内容服务中获取计划上下文
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext == null) {
            return Flux.error(new RuntimeException("计划不存在，请检查计划ID是否正确"));
        }
        return planContext.getPlanStream();
    }

    /**
     * 通知后端前端已完成消费
     */
    @PostMapping("/generate/content/completed")
    public ResponseEntity<String> notifyContentConsumed(@RequestParam("planId") String planId) {
        log.info("前端通知内容已消费完毕，计划ID: {}", planId);

        // 从章节内容服务中获取计划上下文
        PlanContext planContext = chapterContentService.getPlanContextMap().get(planId);
        if (planContext == null) {
            return ResponseEntity.badRequest().body("计划不存在，请检查计划ID是否正确");
        }

        // 通知完成消费
        planContext.notifyConsumptionCompleted();
        planContext.setPlanStream(null);
        return ResponseEntity.ok("通知成功");
    }

    /**
     * 获取章节历史内容
     *
     * @param id 章节ID
     * @return 历史内容列表
     */
    @GetMapping("/{id}/history")
    public Result<JSONObject> getChapterHistory(@PathVariable("id") Long id) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }

        JSONObject historyContent = chapter.getHistoryContent();
        if (historyContent == null) {
            historyContent = new JSONObject();
        }

        return Result.success("Chapter history retrieved successfully", historyContent);
    }

    /**
     * 根据时间戳获取特定版本的章节内容
     *
     * @param id        章节ID
     * @param timestamp 版本时间戳
     * @return 指定版本的章节内容
     */
    @GetMapping("/{id}/history/{timestamp}")
    public Result<String> getChapterHistoryVersion(@PathVariable("id") Long id, @PathVariable("timestamp") String timestamp) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }

        JSONObject historyContent = chapter.getHistoryContent();
        if (historyContent == null || !historyContent.containsKey(timestamp)) {
            return Result.error("History version not found for timestamp: " + timestamp);
        }

        String content = historyContent.getString(timestamp);
        return Result.success("History version retrieved successfully", content);
    }

    /**
     * 从历史版本恢复章节内容
     *
     * @param id        章节ID
     * @param timestamp 版本时间戳
     * @return 恢复后的章节
     */
    @PostMapping("/{id}/history/{timestamp}/restore")
    public Result<Chapter> restoreChapterFromHistory(@PathVariable("id") Long id, @PathVariable("timestamp") String timestamp) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }

        JSONObject historyContent = chapter.getHistoryContent();
        if (historyContent == null || !historyContent.containsKey(timestamp)) {
            return Result.error("History version not found for timestamp: " + timestamp);
        }

        // Get content from history
        String historicalContent = historyContent.getString(timestamp);

        // Save current content to history before restoring
        String currentTimestamp = String.valueOf(System.currentTimeMillis());
        historyContent.put(currentTimestamp, chapter.getContent());

        // Keep only the most recent 10 entries
        if (historyContent.size() > 10) {
            // Find the oldest timestamp key
            String oldestKey = historyContent.keySet().stream()
                    .sorted()
                    .findFirst()
                    .orElse(null);

            // Remove the oldest entry
            if (oldestKey != null) {
                historyContent.remove(oldestKey);
            }
        }

        // Update chapter
        chapter.setContent(historicalContent);
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterService.updateById(chapter);

        return Result.success("Chapter restored from history successfully", chapter);
    }

    /**
     * 删除特定版本的历史记录
     *
     * @param id        章节ID
     * @param timestamp 版本时间戳
     * @return 操作结果
     */
    @DeleteMapping("/{id}/history/{timestamp}")
    public Result<Void> deleteHistoryVersion(@PathVariable("id") Long id, @PathVariable("timestamp") String timestamp) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.error("Chapter not found with id: " + id);
        }

        JSONObject historyContent = chapter.getHistoryContent();
        if (historyContent == null || !historyContent.containsKey(timestamp)) {
            return Result.error("History version not found for timestamp: " + timestamp);
        }

        // Remove the specific history entry
        historyContent.remove(timestamp);

        // Update chapter
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterService.updateById(chapter);

        return Result.success("History version deleted successfully", null);
    }

} 