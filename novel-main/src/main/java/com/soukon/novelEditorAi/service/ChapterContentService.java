package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContentResponse;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 章节内容生成服务接口
 */
public interface ChapterContentService {
    
    /**
     * 生成章节内容
     * @param request 包含生成参数的请求对象
     * @return 生成的章节内容响应
     */
    ChapterContentResponse generateChapterContent(ChapterContentRequest request);
    
    /**
     * 流式生成章节内容
     * @param request 包含生成参数的请求对象
     * @return 生成的章节内容响应
     */
    ChapterContentResponse generateChapterContentStream(ChapterContentRequest request);
    
    /**
     * 新增：流式生成章节内容，返回Flux<String>
     */
    void generateChapterContentStreamFlux(ChapterContentRequest request);
    
    /**
     * 保存生成的章节内容
     * @param chapterId 章节ID
     * @param content 生成的内容
     * @param appendMode 是否追加模式（true表示追加，false表示覆盖）
     * @return 是否保存成功
     */
    boolean saveChapterContent(Long chapterId, String content, Boolean appendMode);
    
    /**
     * 保存生成的章节内容（默认覆盖模式）
     * @param chapterId 章节ID
     * @param content 生成的内容
     * @return 是否保存成功
     */
    default boolean saveChapterContent(Long chapterId, String content) {
        return saveChapterContent(chapterId, content, false);
    }

    /**
     * 创建章节内容生成计划
     * @param request 包含生成参数的请求对象
     * @return 计划ID和状态
     */
    Result<String> generateChapterContentExecute(ChapterContentRequest request);
    
    /**
     * 获取计划上下文映射表
     * @return 计划上下文映射表
     */
    ConcurrentHashMap<String, PlanContext> getPlanContextMap();
} 