package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 条目实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("items")
public class Item {
    
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
     * 描述
     */
    private String description;
} 