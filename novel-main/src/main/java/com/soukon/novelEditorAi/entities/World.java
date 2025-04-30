package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import com.soukon.novelEditorAi.entities.Element;

@Data
@TableName(value = "worlds", autoResultMap = true)
public class World {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private String name;
    private String description;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Element> elements;
    
    private String notes;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 