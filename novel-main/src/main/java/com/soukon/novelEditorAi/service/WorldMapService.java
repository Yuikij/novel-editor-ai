package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.soukon.novelEditorAi.entities.WorldMap;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface WorldMapService extends IService<WorldMap> {
    /**
     * Generate a map based on the provided context description using LLM
     *
     * @param worldId ID of the world to associate with the map
     * @param description Text description to guide the map generation
     * @param width Map width
     * @param height Map height
     * @return The generated map as a WorldMap entity
     */
    Mono<WorldMap> generateMapWithLlm(Long worldId, String description, Integer width, Integer height);
    
    /**
     * Convert the map data to a format suitable for LLM prompt
     *
     * @param worldMap The world map entity
     * @return String representation of the map for prompts
     */
    String toPrompt(WorldMap worldMap);
} 