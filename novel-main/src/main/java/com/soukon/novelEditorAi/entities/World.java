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
    
    /**
     * 生成用于构建生成请求 Prompt 的世界观信息部分。
     *
     * @return 包含世界观名称和描述的字符串。
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isEmpty() && description != null && !description.isEmpty()) {
            sb.append("世界观:\n");
            sb.append(name).append(": ").append(description).append("\n");
        }
        // 可以在这里添加对 elements 的处理逻辑，如果需要的话
        // 例如：
        // if (elements != null && !elements.isEmpty()) {
        //     sb.append("关键元素:\n");
        //     for (Element element : elements) {
        //         sb.append("- ").append(element.getName()).append(": ").append(element.getDescription()).append("\n");
        //     }
        // }
        return sb.toString();
    }
} 