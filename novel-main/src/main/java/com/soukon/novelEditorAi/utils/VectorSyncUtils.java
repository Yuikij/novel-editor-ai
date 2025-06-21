package com.soukon.novelEditorAi.utils;

import com.soukon.novelEditorAi.entities.*;
import com.soukon.novelEditorAi.service.EntitySyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 向量同步工具类
 * 简化Controller中的向量同步集成代码
 */
@Component
@Slf4j
public class VectorSyncUtils {

    @Autowired
    private EntitySyncHelper entitySyncHelper;

    /**
     * 处理实体创建后的向量同步
     */
    public void handleEntityCreate(String entityType, Long entityId, Long projectId) {
        try {
            entitySyncHelper.triggerCreate(entityType, entityId, projectId, false);
            log.debug("触发{}创建向量同步: id={}", entityType, entityId);
        } catch (Exception e) {
            log.error("触发{}创建向量同步失败: id={}, error={}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 处理实体更新后的向量同步
     */
    public void handleEntityUpdate(String entityType, Long entityId, Long version, Long projectId, 
                                   String oldContent, String newContent) {
        try {
            // 检查内容是否真的发生了变更
            if (entitySyncHelper.isContentChanged(oldContent, newContent)) {
                entitySyncHelper.triggerUpdate(entityType, entityId, version, projectId, false);
                log.debug("{}内容已变更，触发向量同步: id={}", entityType, entityId);
            } else {
                log.debug("{}内容未变更，跳过向量同步: id={}", entityType, entityId);
            }
        } catch (Exception e) {
            log.error("触发{}更新向量同步失败: id={}, error={}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 处理实体删除后的向量同步
     */
    public void handleEntityDelete(String entityType, Long entityId, Long projectId) {
        try {
            entitySyncHelper.triggerDelete(entityType, entityId, projectId, true);
            log.debug("触发{}删除向量同步: id={}", entityType, entityId);
        } catch (Exception e) {
            log.error("触发{}删除向量同步失败: id={}, error={}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 处理批量删除后的向量同步
     */
    public void handleBatchDelete(String entityType, java.util.List<? extends Object> entities) {
        try {
            for (Object entity : entities) {
                Long entityId = null;
                Long projectId = null;
                
                if (entity instanceof Chapter) {
                    Chapter chapter = (Chapter) entity;
                    entityId = chapter.getId();
                    projectId = chapter.getProjectId();
                } else if (entity instanceof com.soukon.novelEditorAi.entities.Character) {
                    com.soukon.novelEditorAi.entities.Character character = (com.soukon.novelEditorAi.entities.Character) entity;
                    entityId = character.getId();
                    projectId = character.getProjectId();
                } else if (entity instanceof Plot) {
                    Plot plot = (Plot) entity;
                    entityId = plot.getId();
                    projectId = plot.getProjectId();
                } else if (entity instanceof Project) {
                    Project project = (Project) entity;
                    entityId = project.getId();
                    projectId = project.getId();
                } else if (entity instanceof World) {
                    World world = (World) entity;
                    entityId = world.getId();
                    projectId = null; // World可能没有projectId
                }
                
                if (entityId != null) {
                    entitySyncHelper.triggerDelete(entityType, entityId, projectId, false);
                }
            }
            log.debug("批量触发{}删除向量同步: 数量={}", entityType, entities.size());
        } catch (Exception e) {
            log.error("批量触发{}删除向量同步失败: error={}", entityType, e.getMessage());
        }
    }

    /**
     * 初始化实体的向量版本号
     */
    public void initVectorVersion(Object entity) {
        try {
            if (entity instanceof Chapter) {
                Chapter chapter = (Chapter) entity;
                if (chapter.getVectorVersion() == null) {
                    chapter.setVectorVersion(1L);
                }
            } else if (entity instanceof com.soukon.novelEditorAi.entities.Character) {
                com.soukon.novelEditorAi.entities.Character character = (com.soukon.novelEditorAi.entities.Character) entity;
                if (character.getVectorVersion() == null) {
                    character.setVectorVersion(1L);
                }
            } else if (entity instanceof Plot) {
                Plot plot = (Plot) entity;
                if (plot.getVectorVersion() == null) {
                    plot.setVectorVersion(1L);
                }
            } else if (entity instanceof Project) {
                Project project = (Project) entity;
                if (project.getVectorVersion() == null) {
                    project.setVectorVersion(1L);
                }
            } else if (entity instanceof World) {
                World world = (World) entity;
                if (world.getVectorVersion() == null) {
                    world.setVectorVersion(1L);
                }
            }
        } catch (Exception e) {
            log.error("初始化向量版本号失败: error={}", e.getMessage());
        }
    }

    /**
     * 递增实体的向量版本号
     */
    public void incrementVectorVersion(Object currentEntity, Object existingEntity) {
        try {
            Long currentVersion = null;
            
            if (existingEntity instanceof Chapter) {
                currentVersion = ((Chapter) existingEntity).getVectorVersion();
                ((Chapter) currentEntity).setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
            } else if (existingEntity instanceof com.soukon.novelEditorAi.entities.Character) {
                currentVersion = ((com.soukon.novelEditorAi.entities.Character) existingEntity).getVectorVersion();
                ((com.soukon.novelEditorAi.entities.Character) currentEntity).setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
            } else if (existingEntity instanceof Plot) {
                currentVersion = ((Plot) existingEntity).getVectorVersion();
                ((Plot) currentEntity).setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
            } else if (existingEntity instanceof Project) {
                currentVersion = ((Project) existingEntity).getVectorVersion();
                ((Project) currentEntity).setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
            } else if (existingEntity instanceof World) {
                currentVersion = ((World) existingEntity).getVectorVersion();
                ((World) currentEntity).setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
            }
        } catch (Exception e) {
            log.error("递增向量版本号失败: error={}", e.getMessage());
        }
    }

    /**
     * 构建实体内容用于向量化比较
     */
    public String buildEntityContent(Object entity) {
        if (entity == null) return "";
        
        try {
            if (entity instanceof Chapter) {
                return buildChapterContent((Chapter) entity);
            } else if (entity instanceof com.soukon.novelEditorAi.entities.Character) {
                return buildCharacterContent((com.soukon.novelEditorAi.entities.Character) entity);
            } else if (entity instanceof Plot) {
                return buildPlotContent((Plot) entity);
            } else if (entity instanceof Project) {
                return buildProjectContent((Project) entity);
            } else if (entity instanceof World) {
                return buildWorldContent((World) entity);
            }
        } catch (Exception e) {
            log.error("构建实体内容失败: error={}", e.getMessage());
        }
        
        return "";
    }

    // 私有方法：构建各种实体的内容

    private String buildChapterContent(Chapter chapter) {
        StringBuilder content = new StringBuilder();
        if (chapter.getTitle() != null) {
            content.append("标题: ").append(chapter.getTitle()).append("\n");
        }
        if (chapter.getContent() != null) {
            content.append("内容: ").append(chapter.getContent()).append("\n");
        }
        if (chapter.getSummary() != null) {
            content.append("摘要: ").append(chapter.getSummary()).append("\n");
        }
        if (chapter.getNotes() != null) {
            content.append("备注: ").append(chapter.getNotes()).append("\n");
        }
        return content.toString();
    }

    private String buildCharacterContent(com.soukon.novelEditorAi.entities.Character character) {
        StringBuilder content = new StringBuilder();
        if (character.getName() != null) {
            content.append("姓名: ").append(character.getName()).append("\n");
        }
        if (character.getDescription() != null) {
            content.append("描述: ").append(character.getDescription()).append("\n");
        }
        if (character.getRole() != null) {
            content.append("角色: ").append(character.getRole()).append("\n");
        }
        if (character.getBackground() != null) {
            content.append("背景: ").append(character.getBackground()).append("\n");
        }
        if (character.getNotes() != null) {
            content.append("备注: ").append(character.getNotes()).append("\n");
        }
        return content.toString();
    }

    private String buildPlotContent(Plot plot) {
        StringBuilder content = new StringBuilder();
        if (plot.getTitle() != null) {
            content.append("标题: ").append(plot.getTitle()).append("\n");
        }
        if (plot.getDescription() != null) {
            content.append("描述: ").append(plot.getDescription()).append("\n");
        }
        if (plot.getType() != null) {
            content.append("类型: ").append(plot.getType()).append("\n");
        }

        return content.toString();
    }

    private String buildProjectContent(Project project) {
        StringBuilder content = new StringBuilder();
        if (project.getTitle() != null) {
            content.append("标题: ").append(project.getTitle()).append("\n");
        }
        if (project.getSynopsis() != null) {
            content.append("简介: ").append(project.getSynopsis()).append("\n");
        }
        if (project.getGenre() != null) {
            content.append("类型: ").append(project.getGenre()).append("\n");
        }
        if (project.getStyle() != null) {
            content.append("风格: ").append(project.getStyle()).append("\n");
        }
        return content.toString();
    }

    private String buildWorldContent(World world) {
        StringBuilder content = new StringBuilder();
        if (world.getName() != null) {
            content.append("名称: ").append(world.getName()).append("\n");
        }
        if (world.getDescription() != null) {
            content.append("描述: ").append(world.getDescription()).append("\n");
        }

        if (world.getNotes() != null) {
            content.append("备注: ").append(world.getNotes()).append("\n");
        }
        return content.toString();
    }
} 