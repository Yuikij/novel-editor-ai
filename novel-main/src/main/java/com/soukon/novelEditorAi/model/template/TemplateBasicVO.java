package com.soukon.novelEditorAi.model.template;

import com.soukon.novelEditorAi.annotation.SelectField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板基本信息VO
 * 用于不需要content字段的查询场景
 */
@Data
public class TemplateBasicVO {
    
    @SelectField(description = "模板ID")
    private Long id;
    
    @SelectField(description = "模板名称")
    private String name;
    
    @SelectField(description = "模板标签")
    private String tags;
    
    @SelectField(description = "向量化状态")
    private String vectorStatus;
    
    @SelectField(description = "向量化进度")
    private Integer vectorProgress;
    
    @SelectField(description = "向量化开始时间")
    private LocalDateTime vectorStartTime;
    
    @SelectField(description = "向量化完成时间")
    private LocalDateTime vectorEndTime;
    
    @SelectField(description = "向量化错误信息")
    private String vectorErrorMessage;
    
    // content字段被排除，不会被查询
} 