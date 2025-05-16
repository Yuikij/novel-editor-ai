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
import java.util.Map;

@Data
@TableName(value = "world_maps", autoResultMap = true)
public class WorldMap {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private String name;
    private String description;
    private Long worldId; // Reference to the associated world
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> mapData; // Store the map JSON data
    
    private Integer width;
    private Integer height;
    private String background;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 