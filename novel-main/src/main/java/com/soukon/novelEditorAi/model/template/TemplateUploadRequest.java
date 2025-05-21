package com.soukon.novelEditorAi.model.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模板上传请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateUploadRequest {
    
    /**
     * 模板ID（更新时使用）
     */
    private Long id;
    
    /**
     * 模板名称
     */
    private String name;
    
    /**
     * 模板标签
     */
    private String tags;
    
    /**
     * 模板文件
     */
    private MultipartFile file;
    
    /**
     * 模板内容文本（如果没有文件）
     */
    private String content;
} 