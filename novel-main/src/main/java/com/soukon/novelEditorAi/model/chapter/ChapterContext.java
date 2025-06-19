package com.soukon.novelEditorAi.model.chapter;

import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.World;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 章节上下文信息
 * 包含生成章节内容所需的所有上下文数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContext {
    /**
     * 项目信息
     */
    private Project project;
    
    /**
     * 世界观信息
     */
    private World world;
    
    /**
     * 当前章节信息
     */
    private Chapter currentChapter;

    /**
     * 需要写作的情节
     */
    private Plot firstIncompletePlot;
    
    /**
     * 前一章节（如果有）
     */
    private Chapter previousChapter;
    
    /**
     * 项目的所有角色
     */
    private List<Character> characters;
    
    /**
     * 项目的角色关系
     */
    private List<CharacterRelationship> characterRelationships;
    
    /**
     * 项目的情节点
     */
    private List<OutlinePlotPoint> plotPoints;
    
    /**
     * 关联章节的情节
     */
    private List<Plot> chapterPlots;
    
    /**
     * 本章节的摘要或目标
     */
    private String chapterSummary;

    /**
     * 项目ID
     */
    private Long projectId;
    
    /**
     * 小说标题
     */
    private String novelTitle;
    
    /**
     * 小说概要
     */
    private String novelSummary;
    
    /**
     * 小说风格
     */
    private String novelStyle;
    
    /**
     * 上一章节内容摘要
     */
    private String previousChapterSummary;
    
    /**
     * 下一章节内容摘要
     */
    private String nextChapterSummary;

    /**
     * 下一章节内容摘要
     */
    private Chapter nextChapter;
    
    /**
     * 主要人物列表
     */
    private List<String> mainCharacters;
    
    /**
     * 章节设定的背景
     */
    private String chapterBackground;
    
    /**
     * 写作偏好设置
     */
    private WritingPreference writingPreference;
} 