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
    private String type; // main, subplot, backstory, etc.
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> characterIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * 生成用于构建生成请求 Prompt 的单个情节信息。
     *
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isEmpty()) {
            sb.append("- ").append(description).append("\n");
        }
        // 可以根据需要添加 title 或 type 等信息
        return sb.toString();
    }
} 