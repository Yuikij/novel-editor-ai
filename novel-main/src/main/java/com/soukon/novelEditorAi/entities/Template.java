package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

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
    
    /**
     * 向量化状态：NOT_INDEXED(未索引), INDEXING(索引中), INDEXED(已索引), FAILED(索引失败)
     */
    private String vectorStatus;
    
    /**
     * 向量化进度百分比 (0-100)
     */
    private Integer vectorProgress;
    
    /**
     * 向量化开始时间
     */
    private LocalDateTime vectorStartTime;
    
    /**
     * 向量化完成时间
     */
    private LocalDateTime vectorEndTime;
    
    /**
     * 向量化错误信息
     */
    private String vectorErrorMessage;

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