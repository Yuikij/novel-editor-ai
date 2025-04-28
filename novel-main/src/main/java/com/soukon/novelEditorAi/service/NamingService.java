package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.model.naming.NamingResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 命名服务接口
 * 定义名称生成相关的方法
 */
public interface NamingService {
    
    /**
     * 生成名称
     * @param parameters 生成名称所需参数
     * @return 名称响应对象
     */
    NamingResponse generateNames(Map<String, Object> parameters);
    
    /**
     * 生成名称(流式)
     * @param parameters 生成名称所需参数
     * @return 名称响应流
     */
    Flux<String> generateNamesStream(Map<String, Object> parameters);
} 