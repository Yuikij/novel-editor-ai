package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 模板实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("templates")
public class Template {
    
    /**
     * 雪花id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 名称
     */
    private String name;
    
    /**
     * 标签
     */
    private String tags;
    
    /**
     * 内容
     */
    private String content;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("模板信息").append("\n");
        sb.append("模板名称: ").append(name).append("\n");
        sb.append("模板标签: ").append(tags).append("\n");
        sb.append("模板内容").append(content).append("\n");
        return sb.toString();
    }
}