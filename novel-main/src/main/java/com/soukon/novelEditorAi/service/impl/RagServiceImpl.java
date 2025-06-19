package com.soukon.novelEditorAi.service.impl;

import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.mapper.ChapterMapper;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.mapper.ProjectMapper;
import com.soukon.novelEditorAi.mapper.WorldMapper;
import com.soukon.novelEditorAi.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG服务实现类
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {


    private final ProjectMapper projectMapper;
    private final ChapterMapper chapterMapper;
    private final CharacterMapper characterMapper;
    private final WorldMapper worldMapper;

    @Value("${novel.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${novel.rag.chunk-overlap:50}")
    private int chunkOverlap;


    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private EmbeddingModel embeddingModel;


    @Autowired
    private VectorStore vectorStore;

    @Autowired
    public RagServiceImpl(ProjectMapper projectMapper,
                          ChapterMapper chapterMapper,
                          CharacterMapper characterMapper,
                          WorldMapper worldMapper
    ) {

        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
        this.characterMapper = characterMapper;
        this.worldMapper = worldMapper;
    }


    public void createDocument(String content, Map<String, Object> metadata) {
        // 使用 TextReader 读取文档
        TextReader textReader = new TextReader(content);
        // 设置元数据
        textReader.getCustomMetadata().put("source", "法国");
        // 分块文档
        // 对于文风分析，建议使用更大的分块以保持文本连贯性
        TokenTextSplitter splitter = new TokenTextSplitter(
                500,   // 最大令牌数，减小到 50，确保低于 512
                20,   // 最小令牌数
                5,    // 块重叠令牌数
                Integer.MAX_VALUE, // 移除 maxChunks 限制
                true  // 保留分隔符
        );
        List<Document> documents = splitter.apply(textReader.get());

        log.info("分块后的文档数量: {}", documents.size());

        // 存储到向量存储
        log.info("Adding documents to vector store...");
        vectorStore.add(documents);
        log.info("Documents added successfully.");
    }

    @Override
    public void test() {

        // 加载示例文档
        Resource resource = resourceLoader.getResource("classpath:sample.txt");

        // 使用 TextReader 读取文档
        TextReader textReader = new TextReader(resource);
        // 设置元数据
        textReader.getCustomMetadata().put("source", "法国");
        // 分块文档
        // 对于文风分析，建议使用更大的分块以保持文本连贯性
        TokenTextSplitter splitter = new TokenTextSplitter(
                500,   // 最大令牌数，减小到 50，确保低于 512
                20,   // 最小令牌数
                5,    // 块重叠令牌数
                Integer.MAX_VALUE, // 移除 maxChunks 限制
                true  // 保留分隔符
        );
        List<Document> documents = splitter.apply(textReader.get());

        log.info("分块后的文档数量: {}", documents.size());

        // 存储到向量存储
        log.info("Adding documents to vector store...");
        vectorStore.add(documents);
        log.info("Documents added successfully.");

//        // 示例查询
//        String query = "孙悟空是谁？";
//
//        // 检索相关文档
//        log.info("Performing similarity search for query: {}", query);
//        List<Document> results = vectorStore.similaritySearch(query);
//
//        // 构建上下文
//        StringBuilder context = new StringBuilder();
//        for (Document doc : results) {
//            context.append(doc.getText()).append("\n");
//        }
//
//        // 使用 ChatClient 进行 RAG
//        log.info("Calling ChatClient with context...");
//        String response = chatClient.prompt()
//                .system("你是一个有用的助手。根据以下上下文回答问题：\n" + context)
//                .user(query)
//                .call()
//                .content();
//
//        System.out.println("查询: " + query);
//        System.out.println("回答: " + response);

    }

    @Override
    public void createOrUpdateDocument(String content, String documentId) {
        try {
            // 检查内容是否有效
            if (content == null || content.isEmpty()) {
                log.warn("无法创建或更新文档：内容为空，文档ID: {}", documentId);
                return;
            }

            // 先尝试删除现有文档（及其分块）
            try {
                String filterExpression = "id == \"" + documentId + "\"" +
                                          " || id like \"" + documentId + "-chunk-%\"";
                vectorStore.delete(filterExpression);
                log.debug("已删除现有文档: {}", documentId);
            } catch (Exception e) {
                // 如果删除失败，可能是文档不存在，继续创建
                log.debug("删除现有文档时出现异常，可能是文档不存在: {}", documentId);
            }

            // 创建文档对象
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", documentId);
            metadata.put("type", "custom");
            metadata.put("timestamp", System.currentTimeMillis());

            Document document = new Document(documentId, content, metadata);

            // 添加到向量存储
            vectorStore.add(Collections.singletonList(document));

            log.info("成功创建/更新文档: {}", documentId);
        } catch (Exception e) {
            log.error("创建/更新文档时发生错误: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<Document> retrieveByDocumentId(String documentId, String query) {
        try {
            if (query == null || query.isEmpty()) {
                log.warn("查询为空，无法检索文档: {}", documentId);
                return Collections.emptyList();
            }

            // 创建搜索请求，过滤特定文档ID
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(5)  // 返回最相关的5个结果
                    .similarityThreshold(0.7f)
                    .filterExpression("id == \"" + documentId + "\"" +
                                      " || id like \"" + documentId + "-chunk-%\"")
                    .build();

            // 执行搜索
            List<Document> results = vectorStore.similaritySearch(request);

            log.info("从文档 {} 检索到 {} 个相关结果", documentId, results.size());
            return results;
        } catch (Exception e) {
            log.error("从文档检索时发生错误: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean indexProject(Long projectId) {
        try {
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                log.error("索引项目失败：找不到ID为 {} 的项目", projectId);
                return false;
            }

            // 为项目信息创建文档
            Document projectDoc = new Document(
                    "project-" + projectId,
                    createProjectContent(project),
                    createProjectMetadata(project)
            );

            // 将文档添加到向量存储中
            vectorStore.add(Collections.singletonList(projectDoc));

            log.info("已成功为项目 {} 创建索引", projectId);
            return true;
        } catch (Exception e) {
            log.error("索引项目时发生错误：", e);
            return false;
        }
    }

    @Override
    public boolean indexChapter(Long chapterId) {
        try {
            Chapter chapter = chapterMapper.selectById(chapterId);
            if (chapter == null) {
                log.error("索引章节失败：找不到ID为 {} 的章节", chapterId);
                return false;
            }

            // 获取章节内容
            String content = chapter.getContent();
            if (content == null || content.isEmpty()) {
                log.warn("章节 {} 没有内容，跳过索引", chapterId);
                return true;
            }

            // 限制处理的文本长度，避免内存溢出
            final int MAX_INDEXABLE_LENGTH = 50000; // 设置最大可索引长度
            if (content.length() > MAX_INDEXABLE_LENGTH) {
                log.warn("章节 {} 内容过长 ({}字符)，将只索引前 {} 字符",
                        chapterId, content.length(), MAX_INDEXABLE_LENGTH);
                content = content.substring(0, MAX_INDEXABLE_LENGTH);
            }

            // 将章节内容分块
            List<String> chunks = chunkText(content, chunkSize, chunkOverlap);
            List<Document> documents = new ArrayList<>();

            // 为每个块创建文档
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = createChapterMetadata(chapter);
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());

                Document doc = new Document(
                        "chapter-" + chapterId + "-chunk-" + i,
                        chunks.get(i),
                        metadata
                );
                documents.add(doc);
            }

            // 将文档添加到向量存储中
            vectorStore.add(documents);

            log.info("已成功为章节 {} 创建索引，共 {} 个块", chapterId, chunks.size());
            return true;
        } catch (Exception e) {
            log.error("索引章节时发生错误：", e);
            return false;
        }
    }

    @Override
    public boolean indexCharacter(Long characterId) {
        try {
            Character character = characterMapper.selectById(characterId);
            if (character == null) {
                log.error("索引角色失败：找不到ID为 {} 的角色", characterId);
                return false;
            }

            // 为角色信息创建文档
            Document characterDoc = new Document(
                    "character-" + characterId,
                    createCharacterContent(character),
                    createCharacterMetadata(character)
            );

            // 将文档添加到向量存储中
            vectorStore.add(Collections.singletonList(characterDoc));

            log.info("已成功为角色 {} 创建索引", characterId);
            return true;
        } catch (Exception e) {
            log.error("索引角色时发生错误：", e);
            return false;
        }
    }

    @Override
    public boolean indexWorld(Long worldId) {
        try {
            World world = worldMapper.selectById(worldId);
            if (world == null) {
                log.error("索引世界观失败：找不到ID为 {} 的世界观", worldId);
                return false;
            }

            // 为世界观信息创建文档
            Document worldDoc = new Document(
                    "world-" + worldId,
                    createWorldContent(world),
                    createWorldMetadata(world)
            );

            // 将文档添加到向量存储中
            vectorStore.add(Collections.singletonList(worldDoc));

            log.info("已成功为世界观 {} 创建索引", worldId);
            return true;
        } catch (Exception e) {
            log.error("索引世界观时发生错误：", e);
            return false;
        }
    }

    @Override
    public List<Document> retrieveByProjectId(Long projectId, String query, int maxResults) {
        try {
            // 创建搜索请求
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .similarityThreshold(0.7f)
                    .filterExpression("projectId == " + projectId)
                    .build();

            // 执行相似度搜索
            List<Document> results = vectorStore.similaritySearch(request);

            log.info("查询 '{}' 返回了 {} 个相关文档", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("检索相关文档时发生错误：", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Document> retrieveRelevantForChapter(Long chapterId, int maxResults) {
        try {
            Chapter chapter = chapterMapper.selectById(chapterId);
            if (chapter == null) {
                log.error("检索章节相关文档失败：找不到ID为 {} 的章节", chapterId);
                return Collections.emptyList();
            }

            // 使用章节摘要作为查询
            String query = chapter.getSummary();
            if (query == null || query.isEmpty()) {
                query = chapter.getTitle();
            }

            // 创建搜索请求
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .similarityThreshold(0.7f)
                    .filterExpression("projectId == " + chapter.getProjectId() +
                                      " && id != " + "\"chapter-" + chapterId + "\"")
                    .build();

            // 执行相似度搜索
            List<Document> results = vectorStore.similaritySearch(request);

            log.info("为章节 {} 检索到 {} 个相关文档", chapterId, results.size());
            return results;
        } catch (Exception e) {
            log.error("检索章节相关文档时发生错误：", e);
            return Collections.emptyList();
        }
    }

    /**
     * 将文本分块，优化内存使用
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        if (textLength <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // 估计分块数量并预分配容量，减少动态扩容
        int estimatedChunks = (textLength / (chunkSize - overlap)) + 1;
        chunks = new ArrayList<>(estimatedChunks);

        int start = 0;
        while (start < textLength) {
            // 计算结束位置，避免越界
            int end = Math.min(start + chunkSize, textLength);

            // 优化边界查找逻辑
            if (end < textLength) {
                // 限制搜索范围，避免过度搜索
                int searchLimit = Math.min(50, end - start); // 最多向前/后搜索50个字符
                int sentenceEnd = findOptimizedSentenceBoundary(text, end, searchLimit);
                if (sentenceEnd > start) { // 确保边界有效
                    end = sentenceEnd;
                }
            }

            // 安全创建子字符串
            String chunk = null;
            try {
                chunk = text.substring(start, end);
            } catch (OutOfMemoryError e) {
                log.error("创建文本块时内存溢出，位置: [{}, {}], 文本总长度: {}", start, end, textLength);
                // 创建一个更小的块，或者放弃当前块
                if (end - start > 1000) {
                    end = start + 1000; // 尝试更小的块大小
                    chunk = text.substring(start, end);
                } else {
                    // 如果即使很小的块也无法创建，则跳过
                    break;
                }
            }

            if (chunk != null) {
                chunks.add(chunk);
            }

            // 下一个块的起始位置，考虑重叠
            start = end - overlap;
            if (start < 0 || start >= end) { // 确保进度是向前的
                start = end;
            }

            // 控制块数量，防止无限循环
            if (chunks.size() >= 2 * estimatedChunks) {
                log.warn("块数量超过预期，可能有循环问题，强制结束: {}/{}", chunks.size(), estimatedChunks);
                break;
            }
        }

        return chunks;
    }

    /**
     * 优化的句子边界查找方法
     */
    private int findOptimizedSentenceBoundary(String text, int position, int searchLimit) {
        // 向前查找指定范围内的句子结束符
        int forward = Math.min(position + searchLimit, text.length());
        for (int i = position; i < forward; i++) {
            if (isSentenceEnd(text.charAt(i))) {
                return i + 1;
            }
        }

        // 向后查找指定范围内的句子结束符
        int backward = Math.max(position - searchLimit, 0);
        for (int i = position; i > backward; i--) {
            if (isSentenceEnd(text.charAt(i))) {
                return i + 1;
            }
        }

        // 没找到句子边界，直接返回原位置
        return position;
    }

    /**
     * 判断字符是否为句子结束符
     */
    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?';
    }

    /**
     * 创建项目内容文本
     */
    private String createProjectContent(Project project) {
        StringBuilder content = new StringBuilder();
        content.append("项目标题: ").append(project.getTitle()).append("\n");
        content.append("类型: ").append(project.getGenre()).append("\n");
        content.append("风格: ").append(project.getStyle()).append("\n");
        content.append("简介: ").append(project.getSynopsis()).append("\n");

        if (project.getTags() != null && !project.getTags().isEmpty()) {
            content.append("标签: ").append(String.join(", ", project.getTags())).append("\n");
        }

        content.append("目标受众: ").append(project.getTargetAudience()).append("\n");

        if (project.getWritingRequirements() != null && !project.getWritingRequirements().isEmpty()) {
            content.append("写作要求:\n");
            for (String req : project.getWritingRequirements()) {
                content.append("- ").append(req).append("\n");
            }
        }

        return content.toString();
    }

    /**
     * 创建项目元数据
     */
    private Map<String, Object> createProjectMetadata(Project project) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "project-" + project.getId());
        metadata.put("projectId", project.getId());
        metadata.put("type", "project");
        metadata.put("title", project.getTitle());
        metadata.put("genre", project.getGenre());
        metadata.put("style", project.getStyle());
        return metadata;
    }

    /**
     * 创建章节元数据
     */
    private Map<String, Object> createChapterMetadata(Chapter chapter) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "chapter-" + chapter.getId());
        metadata.put("projectId", chapter.getProjectId());
        metadata.put("chapterId", chapter.getId());
        metadata.put("type", "chapter");
        metadata.put("title", chapter.getTitle());
        metadata.put("sortOrder", chapter.getSortOrder());
        return metadata;
    }

    /**
     * 创建角色内容文本
     */
    private String createCharacterContent(Character character) {
        StringBuilder content = new StringBuilder();
        content.append("角色名称: ").append(character.getName()).append("\n");
        content.append("角色描述: ").append(character.getDescription()).append("\n");
        content.append("角色类型: ").append(character.getRole()).append("\n");

        if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
            content.append("性格特点:\n");
            for (String trait : character.getPersonality()) {
                content.append("- ").append(trait).append("\n");
            }
        }

        if (character.getGoals() != null && !character.getGoals().isEmpty()) {
            content.append("目标:\n");
            for (String goal : character.getGoals()) {
                content.append("- ").append(goal).append("\n");
            }
        }

        if (character.getBackground() != null && !character.getBackground().isEmpty()) {
            content.append("背景故事: ").append(character.getBackground()).append("\n");
        }

        return content.toString();
    }

    /**
     * 创建角色元数据
     */
    private Map<String, Object> createCharacterMetadata(Character character) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "character-" + character.getId());
        metadata.put("projectId", character.getProjectId());
        metadata.put("characterId", character.getId());
        metadata.put("type", "character");
        metadata.put("name", character.getName());
        metadata.put("role", character.getRole());
        return metadata;
    }

    /**
     * 创建世界观内容文本
     */
    private String createWorldContent(World world) {
        StringBuilder content = new StringBuilder();
        content.append("世界名称: ").append(world.getName()).append("\n");
        content.append("世界描述: ").append(world.getDescription()).append("\n");

        if (world.getElements() != null && !world.getElements().isEmpty()) {
            content.append("世界元素:\n");
            for (com.soukon.novelEditorAi.entities.Element element : world.getElements()) {
                content.append("- ").append(element.getType()).append(": ").append(element.getName()).append("\n");
                content.append("  ").append(element.getDescription()).append("\n");
                if (element.getDetails() != null && !element.getDetails().isEmpty()) {
                    content.append("  详情: ").append(element.getDetails()).append("\n");
                }
            }
        }

        if (world.getNotes() != null && !world.getNotes().isEmpty()) {
            content.append("备注: ").append(world.getNotes()).append("\n");
        }

        return content.toString();
    }

    /**
     * 创建世界观元数据
     */
    private Map<String, Object> createWorldMetadata(World world) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", "world-" + world.getId());
        metadata.put("worldId", world.getId());
        metadata.put("type", "world");
        metadata.put("name", world.getName());
        return metadata;
    }
} 