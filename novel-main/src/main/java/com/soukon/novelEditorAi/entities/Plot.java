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
@TableName(value = "plots", autoResultMap = true)
public class Plot {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long projectId;
    private Long chapterId;
    private String title;
    private String description;
    private Integer sortOrder;
    //  类型结构
    private String type;
    private String status;
    private Integer completionPercentage;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> characterIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
} 