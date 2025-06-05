package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.model.chapter.ChapterListDTO;

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
    
    /**
     * 分页查询章节列表（不包含content和historyContent字段）
     * @param page 页码
     * @param size 每页大小
     * @param projectId 项目ID（可选）
     * @param title 章节标题（可选）
     * @param status 章节状态（可选）
     * @return 分页结果
     */
    Page<ChapterListDTO> pageChapterList(int page, int size, Long projectId, String title, String status);
    
    /**
     * 根据项目ID查询章节列表（不包含content和historyContent字段）
     * @param projectId 项目ID
     * @return 章节列表
     */
    List<ChapterListDTO> getChapterListByProjectId(Long projectId);
    
    /**
     * 查询所有章节列表（不包含content和historyContent字段）
     * @return 章节列表
     */
    List<ChapterListDTO> getAllChapterList();

    /**
     * 获取章节最新内容，支持指定字数截取
     * 如果当前章节没有内容，会向前查找上一章节的内容
     *
     * @param chapterId 章节ID
     * @param wordCount 需要获取的字数（为null时返回全部内容）
     * @return 章节内容字符串，如果没有找到任何内容则返回空字符串
     */
    String getLatestChapterContent(Long chapterId, Integer wordCount);

    /**
     * 验证并处理章节的sortOrder，确保在同一项目中不重复
     * 如果发生重复，会自动调整后续章节的sortOrder
     *
     * @param chapter 要保存或更新的章节
     * @param isUpdate 是否为更新操作（true为更新，false为新增）
     * @throws IllegalArgumentException 如果参数无效
     */
    void validateAndHandleSortOrder(Chapter chapter, boolean isUpdate);

    /**
     * 重新整理项目中所有章节的sortOrder，确保连续且无重复
     * 
     * @param projectId 项目ID
     * @return 重新整理的章节数量
     */
    int reorderChaptersByProject(Long projectId);
}