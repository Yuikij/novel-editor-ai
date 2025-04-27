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
@TableName(value = "characters", autoResultMap = true)
public class Character {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Integer projectId;
    private String name;
    private String role;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> personality;
    
    private String background;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> goals;
    
    private String avatarUrl;
    private String notes;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 