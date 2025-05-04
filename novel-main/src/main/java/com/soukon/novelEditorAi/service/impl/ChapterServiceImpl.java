package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.service.ChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements ChapterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    @Autowired
    private ChapterMapper chapterMapper;
    
    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分。
     *
     * @param chapter 章节实体
     * @param previousChapterSummary 上一章节的摘要（如果存在）。
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    @Override
    public String toPrompt(Chapter chapter, String previousChapterSummary) {
        if (chapter == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        if (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) {
            sb.append("章节标题: ").append(chapter.getTitle()).append("\n");
        }
        if (chapter.getType() != null && !chapter.getType().isEmpty()) {
            sb.append("章节类型: ").append(chapter.getType()).append("\n");
        }

        if (chapter.getWordCountGoal() != null) {
            sb.append("章节目标字数: ").append(chapter.getWordCountGoal()).append("\n");
        }
        if (chapter.getSummary() != null && !chapter.getSummary().isEmpty()) {
            sb.append("章节摘要: ").append(chapter.getSummary()).append("\n");
        }
        if (chapter.getNotes() != null && !chapter.getNotes().isEmpty()) { // 使用 notes 作为章节背景
            sb.append("章节背景: ").append(chapter.getNotes()).append("\n");
        }
        
        if (previousChapterSummary != null && !previousChapterSummary.isEmpty()) {
            sb.append("上一章节摘要: ").append(previousChapterSummary).append("\n");
        }
        
        // 添加章节位置信息
        Long projectId = chapter.getProjectId();
        Integer chapterPosition = chapter.getSortOrder();
        if (projectId != null && chapterPosition != null) {
            // 查询项目总章节数
            LambdaQueryWrapper<Chapter> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Chapter::getProjectId, projectId);
            long totalChaptersLong = count(queryWrapper);
            int totalChapters = (int) totalChaptersLong;
            
            sb.append("章节位置: 第").append(chapterPosition).append("章 (共").append(totalChapters).append("章)\n");
        }
        
        return sb.toString();
    }
} 