package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.model.template.TemplateVectorProgressDTO;
import reactor.core.publisher.Flux;

/**
 * 模板向量化服务接口
 */
public interface TemplateVectorService {
    
    /**
     * 查询模板向量化进度
     * @param templateId 模板ID
     * @return 向量化进度信息
     */
    Result<TemplateVectorProgressDTO> getVectorProgress(Long templateId);
    
    /**
     * 手动导入模板到向量数据库（覆盖模式）
     * @param templateId 模板ID
     * @return 导入结果
     */
    Result<Boolean> indexTemplate(Long templateId);
    
    /**
     * 异步导入模板到向量数据库
     * @param templateId 模板ID
     * @return 导入结果
     */
    Result<Boolean> indexTemplateAsync(Long templateId);
    
    /**
     * 删除模板的向量索引
     * @param templateId 模板ID
     * @return 删除结果
     */
    Result<Boolean> deleteTemplateIndex(Long templateId);
    
    /**
     * 批量导入模板到向量数据库
     * @param templateIds 模板ID列表
     * @return 导入结果
     */
    Result<Boolean> batchIndexTemplates(java.util.List<Long> templateIds);
    
    /**
     * 流式获取向量化进度
     * @param templateId 模板ID
     * @return 进度流
     */
    Flux<TemplateVectorProgressDTO> getVectorProgressStream(Long templateId);
} 