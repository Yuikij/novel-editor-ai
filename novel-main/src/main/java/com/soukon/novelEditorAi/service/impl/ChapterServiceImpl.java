package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.service.ChapterService;
import org.springframework.stereotype.Service;

@Service
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements ChapterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
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
        if (chapter.getSummary() != null && !chapter.getSummary().isEmpty()) {
            sb.append("章节摘要: ").append(chapter.getSummary()).append("\n");
        }
        if (previousChapterSummary != null && !previousChapterSummary.isEmpty()) {
            sb.append("上一章节摘要: ").append(previousChapterSummary).append("\n");
        }
        if (chapter.getNotes() != null && !chapter.getNotes().isEmpty()) { // 使用 notes 作为章节背景
            sb.append("章节背景: ").append(chapter.getNotes()).append("\n");
        }
        return sb.toString();
    }
} 