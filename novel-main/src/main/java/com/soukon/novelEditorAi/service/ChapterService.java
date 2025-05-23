package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Chapter;

import java.util.List;

public interface ChapterService extends IService<Chapter> {
    // MyBatis-Plus provides basic CRUD operations through IService
    // You can declare custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分。
     *
     * @param chapter 章节实体
     * @param previousChapterSummary 上一章节的摘要（如果存在）。
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    String toPrompt(Chapter chapter, String previousChapterSummary);
    
    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分，自动查询上一章节的摘要。
     *
     * @param chapter 章节实体
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    String toPrompt(Chapter chapter);

    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分，自动查询上一章节的摘要。
     *
     * @param projectId 项目ID
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    String toPromptProjectId(Long projectId);

    String toPromptChapterId(Long chapterId);
    
    /**
     * 根据已有的章节，补全或扩展章节列表到目标数量
     *
     * @param projectId 项目ID
     * @param existingChapterIds 已有的章节ID列表
     * @param targetCount 目标章节总数
     * @return 补全后的章节列表（包含已有的和新生成的）
     */
    List<Chapter> expandChapters(Long projectId, List<Long> existingChapterIds, Integer targetCount);
}