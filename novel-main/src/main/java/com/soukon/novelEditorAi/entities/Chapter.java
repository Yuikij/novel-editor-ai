package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Data
@TableName("chapters")
public class Chapter {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long projectId;
    private String title;
    private Integer sortOrder;
    private String status; // Enum: 'draft', 'in-progress', 'completed', 'edited'
    private String summary;
    private String notes;
    private Long wordCountGoal;
    private Long wordCount;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * 生成用于构建生成请求 Prompt 的章节信息部分。
     *
     * @param previousChapterSummary 上一章节的摘要（如果存在）。
     * @return 包含章节标题、摘要、背景和上一章节摘要的字符串。
     */
    public String toPrompt(String previousChapterSummary) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append("章节标题: ").append(title).append("\n");
        }
        if (summary != null && !summary.isEmpty()) {
            sb.append("章节摘要: ").append(summary).append("\n");
        }
        if (previousChapterSummary != null && !previousChapterSummary.isEmpty()) {
            sb.append("上一章节摘要: ").append(previousChapterSummary).append("\n");
        }
        if (notes != null && !notes.isEmpty()) { // 使用 notes 作为章节背景
            sb.append("章节背景: ").append(notes).append("\n");
        }
        return sb.toString();
    }
} 