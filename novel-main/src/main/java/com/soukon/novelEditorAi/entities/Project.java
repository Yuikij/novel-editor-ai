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
import java.util.stream.Collectors;

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
    
    /**
     * 生成用于构建生成请求 Prompt 的项目信息部分。
     *
     * @return 包含小说标题、概要和风格的字符串。
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append("小说标题: ").append(title).append("\n");
        }
        if (synopsis != null && !synopsis.isEmpty()) {
            sb.append("小说概要: ").append(synopsis).append("\n");
        }
        if (style != null && !style.isEmpty()) {
            sb.append("小说风格: ").append(style).append("\n");
        }
        // 可以根据需要添加更多字段，例如 genre, targetAudience 等
        return sb.toString();
    }
} 