package com.soukon.novelEditorAi.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * VectorStore配置类
 * 使用动态代理提供VectorStore实现
 */
@Configuration
public class VectorStoreConfig {
    
    /**
     * 配置VectorStore
     * 使用动态代理处理接口更改的情况
     */
    @Bean
    @Primary
    public VectorStore vectorStore() {
        return (VectorStore) Proxy.newProxyInstance(
            VectorStore.class.getClassLoader(),
            new Class<?>[] { VectorStore.class },
            new VectorStoreInvocationHandler()
        );
    }
    
    /**
     * VectorStore的动态代理处理器
     * 实现基本操作，忽略不需要的方法
     */
    private static class VectorStoreInvocationHandler implements InvocationHandler {
        private final List<Document> documents = new ArrayList<>();
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            
            // 添加文档
            if ("add".equals(methodName) && args.length > 0 && args[0] instanceof List) {
                @SuppressWarnings("unchecked")
                List<Document> docs = (List<Document>) args[0];
                documents.addAll(docs);
                return null;
            }
            
            // 检索文档（支持两种调用方式）
            if ("similaritySearch".equals(methodName)) {
                return new ArrayList<>(documents);
            }
            
            // 删除文档（任何删除方法）
            if ("delete".equals(methodName)) {
                if (args.length > 0 && args[0] instanceof List) {
                    // 处理delete(List<String> ids)
                    @SuppressWarnings("unchecked")
                    List<String> idList = (List<String>) args[0];
                    documents.removeIf(doc -> idList.contains(doc.getId()));
                }
                // 忽略其他delete方法
                return null;
            }
            
            // 对于未实现的方法，返回适当的默认值
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(Void.TYPE)) {
                return null;
            } else if (List.class.isAssignableFrom(returnType)) {
                return new ArrayList<>();
            } else if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
                return false;
            } else if (int.class.equals(returnType) || Integer.class.equals(returnType)) {
                return 0;
            } else if (long.class.equals(returnType) || Long.class.equals(returnType)) {
                return 0L;
            } else if (double.class.equals(returnType) || Double.class.equals(returnType)) {
                return 0.0;
            } else if (float.class.equals(returnType) || Float.class.equals(returnType)) {
                return 0.0f;
            }
            
            return null;
        }
    }
} 