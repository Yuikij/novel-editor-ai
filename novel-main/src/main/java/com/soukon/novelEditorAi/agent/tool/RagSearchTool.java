package com.soukon.novelEditorAi.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.soukon.novelEditorAi.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;

/**
 * RAG搜索工具
 * 让LLM能够搜索相关文档来辅助写作
 */
@Slf4j
public class RagSearchTool implements WritingToolCallback {
    
    private static final String TOOL_NAME = "rag_search";
    private static final String DESCRIPTION = """
            搜索项目相关的文档内容，用于写作时获取相关背景信息。
            可以搜索角色信息、世界观设定、章节内容等已有的文档。
            返回与查询最相关的文档片段，帮助保持写作的一致性。
            """;
    
    private static final String PARAMETERS = """
            {
                "type": "object",
                "properties": {
                    "projectId": {
                        "type": "integer",
                        "description": "项目ID，用于限定搜索范围"
                    },
                    "query": {
                        "type": "string",
                        "description": "搜索查询，描述你想要找到的信息"
                    },
                    "maxResults": {
                        "type": "integer",
                        "description": "最大返回结果数，默认为5",
                        "default": 5
                    },
                    "searchType": {
                        "type": "string",
                        "enum": ["general", "character", "world", "chapter"],
                        "description": "搜索类型：general(通用), character(角色), world(世界观), chapter(章节)",
                        "default": "general"
                    }
                },
                "required": ["projectId", "query"]
            }
            """;
    
    private final RagService ragService;
    private String planId;
    private String lastSearchQuery = "";
    private String lastSearchResult = "";
    
    public RagSearchTool(RagService ragService) {
        this.ragService = ragService;
    }
    
    @Override
    public WritingToolResult apply(String input, ToolContext toolContext) {
        try {
            log.info("RAG搜索工具输入: {}", input);
            
            Map<String, Object> params = JSON.parseObject(input, new TypeReference<Map<String, Object>>() {});
            
            Long projectId = Long.valueOf(params.get("projectId").toString());
            String query = (String) params.get("query");
            Integer maxResults = params.get("maxResults") != null ? 
                    Integer.valueOf(params.get("maxResults").toString()) : 5;
            String searchType = params.get("searchType") != null ? 
                    (String) params.get("searchType") : "general";
            
            // 执行RAG搜索
            List<Document> documents = ragService.retrieveByProjectId(projectId, query, maxResults);
            
            // 格式化搜索结果
            String result = formatSearchResults(documents, query, searchType);
            
            lastSearchQuery = query;
            lastSearchResult = result;
            
            log.info("RAG搜索结果: 找到 {} 个相关文档", documents.size());
            return WritingToolResult.success(result);
            
        } catch (Exception e) {
            log.error("RAG搜索工具执行失败", e);
            return WritingToolResult.failure("搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 格式化搜索结果
     */
    private String formatSearchResults(List<Document> documents, String query, String searchType) {
        if (documents.isEmpty()) {
            return String.format("未找到与查询 \"%s\" 相关的文档。", query);
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== RAG搜索结果 ===\n");
        result.append("查询: ").append(query).append("\n");
        result.append("搜索类型: ").append(searchType).append("\n");
        result.append("找到 ").append(documents.size()).append(" 个相关文档\n\n");
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            result.append("**文档 ").append(i + 1).append("**\n");
            
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
    
    @Override
    public String getName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
    
    @Override
    public String getParameters() {
        return PARAMETERS;
    }
    
    @Override
    public Class<?> getInputType() {
        return String.class;
    }
    
    @Override
    public boolean isReturnDirect() {
        return false;
    }
    
    @Override
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    @Override
    public String getCurrentToolState() {
        return String.format("最后搜索: %s | 结果长度: %d", 
                lastSearchQuery, lastSearchResult.length());
    }
    
    @Override
    public void cleanup(String planId) {
        this.lastSearchQuery = "";
        this.lastSearchResult = "";
    }
    
    @Override
    public ToolCallback toSpringAiToolCallback() {
        return FunctionToolCallback.builder(TOOL_NAME, this)
                .description(DESCRIPTION)
                .inputSchema(PARAMETERS)
                .inputType(String.class)
                .build();
    }
} 