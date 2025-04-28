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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private final VectorStore vectorStore;
    private final ProjectMapper projectMapper;
    private final ChapterMapper chapterMapper;
    private final CharacterMapper characterMapper;
    private final WorldMapper worldMapper;

    @Value("${novel.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${novel.rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Autowired
    public RagServiceImpl(VectorStore vectorStore,
                          ProjectMapper projectMapper,
                          ChapterMapper chapterMapper,
                          CharacterMapper characterMapper,
                          WorldMapper worldMapper) {
        this.vectorStore = vectorStore;
        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
        this.characterMapper = characterMapper;
        this.worldMapper = worldMapper;
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
     * 将文本分块
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

        int start = 0;
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            // 如果不是最后一块且不在句子边界，尝试在句子边界处截断
            if (end < textLength) {
                int sentenceEnd = findSentenceBoundary(text, end);
                if (sentenceEnd > 0) {
                    end = sentenceEnd;
                }
            }

            chunks.add(text.substring(start, end));

            // 下一个块的起始位置，考虑重叠
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }

        return chunks;
    }

    /**
     * 查找句子边界
     */
    private int findSentenceBoundary(String text, int around) {
        // 向前查找100个字符以内的句子结束符
        int forward = Math.min(around + 100, text.length());
        for (int i = around; i < forward; i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }

        // 向后查找100个字符以内的句子结束符
        int backward = Math.max(around - 100, 0);
        for (int i = around; i > backward; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }

        // 没找到句子边界，直接返回原位置
        return around;
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