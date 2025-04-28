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
} 