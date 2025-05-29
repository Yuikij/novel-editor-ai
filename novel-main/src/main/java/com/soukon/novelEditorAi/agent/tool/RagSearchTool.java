package com.soukon.novelEditorAi.agent.tool;

import com.soukon.novelEditorAi.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG搜索工具
 * 让LLM能够搜索项目相关文档来辅助写作
 */
@Component
@Slf4j
public class RagSearchTool {

//    类型: 世界观,章节,情节,内容,大纲,模板
    
    private final RagService ragService;
    private String planId;
    private String lastSearchResult = "";
    
    public RagSearchTool(RagService ragService) {
        this.ragService = ragService;
    }
    
    @Tool(name = "rag_search", description = """
            搜索项目相关的文档内容，用于写作时获取相关背景信息。
            可以搜索角色信息、世界观设定、章节内容等已有的文档。
            返回与查询最相关的文档片段，帮助保持写作的一致性。
            """)
    public String searchDocuments(
            Long projectId,
            String query,
            Integer maxResults,
            String searchType) {
        
        try {
            log.info("RAG搜索工具调用: projectId={}, query={}, maxResults={}, searchType={}", 
                    projectId, query, maxResults, searchType);
            
            // 设置默认值
            if (maxResults == null || maxResults <= 0) {
                maxResults = 5;
            }
            if (searchType == null) {
                searchType = "general";
            }
            
            // 执行搜索
            List<Document> documents = ragService.retrieveByProjectId(
                    projectId, query, maxResults);
            
            String result = formatSearchResults(documents, query);
            lastSearchResult = result;
            
            log.info("RAG搜索结果: 找到 {} 个相关文档片段", documents.size());
            return result;
            
        } catch (Exception e) {
            log.error("RAG搜索工具执行失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
    
    /**
     * 格式化搜索结果
     */
    private String formatSearchResults(List<Document> documents, String query) {
        if (documents.isEmpty()) {
            return "未找到与查询 \"" + query + "\" 相关的文档内容。";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== RAG搜索结果 ===\n");
        result.append("查询: ").append(query).append("\n");
        result.append("找到 ").append(documents.size()).append(" 个相关文档片段:\n\n");
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            result.append("**片段 ").append(i + 1).append("**:\n");
            
            // 添加文档元数据信息
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata != null) {
                String docType = (String) metadata.get("type");
                if (docType != null) {
                    result.append("类型: ").append(docType).append("\n");
                }
                
                String docName = (String) metadata.get("name");
                if (docName != null) {
                    result.append("名称: ").append(docName).append("\n");
                }
                
                String title = (String) metadata.get("title");
                if (title != null) {
                    result.append("标题: ").append(title).append("\n");
                }
            }
            
            // 添加文档内容
            String content = doc.getText();
            if (content != null) {
                // 限制内容长度，避免输出过长
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                result.append("内容: ").append(content).append("\n");
            }
            
            result.append("\n---\n\n");
        }
        
        return result.toString();
    }
    
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    public String getCurrentToolState() {
        return "最后搜索结果: " + (lastSearchResult.length() > 100 ? 
                lastSearchResult.substring(0, 100) + "..." : lastSearchResult);
    }
    
    public void cleanup(String planId) {
        this.lastSearchResult = "";
    }
} 