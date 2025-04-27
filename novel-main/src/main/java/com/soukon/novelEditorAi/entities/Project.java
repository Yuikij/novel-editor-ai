package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "projects", autoResultMap = true)
public class Project {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private String title;
    private String genre;
    private String style;
    private String synopsis;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    private String targetAudience;
    private Long wordCountGoal;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> highlights;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> writingRequirements;
    
    private String status; // Enum: 'draft', 'in-progress', 'completed', 'published'
    private Long worldId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 