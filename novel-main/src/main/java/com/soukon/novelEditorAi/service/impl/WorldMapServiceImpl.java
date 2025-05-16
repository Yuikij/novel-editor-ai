package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.WorldMap;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.mapper.WorldMapMapper;
import com.soukon.novelEditorAi.service.WorldMapService;
import com.soukon.novelEditorAi.service.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorldMapServiceImpl extends ServiceImpl<WorldMapMapper, WorldMap> implements WorldMapService {

    private static final Logger log = LoggerFactory.getLogger(WorldMapServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private LlmService llmService;
    
    @Autowired
    private WorldService worldService;
    
    private static final String MAP_GENERATION_SYSTEM_PROMPT = """
            You are a fantasy map generation expert. Your task is to create detailed and realistic maps based on text descriptions.
            
            For map generation:
            1. Analyze the description carefully to understand the geography, terrain, and notable features.
            2. Create a map structure that accurately represents the described world.
            3. Return ONLY valid JSON in the exact format shown in the example, with no explanations or comments.
            
            The map output should be a JSON object with the following structure:
            {
              "maps": [
                {
                  "id": "uniqueId", 
                  "name": "Map Name",
                  "width": int,
                  "height": int,
                  "regions": [
                    {
                      "id": "uniqueId",
                      "name": "Region Name",
                      "path": [{"x": int, "y": int}, ...],
                      "type": "region",
                      "color": "rgba color string",
                      "borderColor": "color hex",
                      "description": "region description"
                    }
                  ],
                  "locations": [],
                  "background": "color hex",
                  "terrainFeatures": {
                    "waters": [
                      {
                        "path": [{"x": int, "y": int}, ...],
                        "type": "lake or river"
                      }
                    ],
                    "deserts": [],
                    "forests": [],
                    "mountains": []
                  }
                }
              ]
            }
            
            For each feature, generate realistic coordinates and shapes. Ensure all IDs are random strings.
            The response must be ONLY the JSON structure with no additional text.
            """;

    @Override
    public Mono<WorldMap> generateMapWithLlm(Long worldId, String description, Integer width, Integer height) {
        World world = worldService.getById(worldId);
        if (world == null) {
            return Mono.error(new RuntimeException("World not found with id: " + worldId));
        }
        
        log.info("Generating map for world: {} with description: {}", world.getName(), description);
        
        // Create system message with instructions
        Message systemMessage = new SystemMessage(MAP_GENERATION_SYSTEM_PROMPT);
        
        // Create context message with world information and description
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Create a map for the following world:\n\n");
        contextBuilder.append("World name: ").append(world.getName()).append("\n");
        contextBuilder.append("World description: ").append(world.getDescription()).append("\n\n");
        contextBuilder.append("Map specifics: ").append(description).append("\n");
        contextBuilder.append("Map dimensions: ").append(width).append("x").append(height).append("\n");
        contextBuilder.append("Incorporate elements from the world description into the map. Add relevant terrain features.");
        
        Message userMessage = new UserMessage(contextBuilder.toString());
        
        // Create prompt with both messages
        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);
        Prompt prompt = new Prompt(messages);
        
        return Mono.fromCallable(() -> {
            // Call LLM to generate map JSON using a ChatClient
            ChatClient chatClient = ChatClient.builder(llmService.getChatModel()).build();
            String response = chatClient.prompt(prompt).call().content();
            log.debug("LLM response: {}", response);
            
            try {
                // Parse response to extract valid JSON
                Map<String, Object> mapData = parseJsonResponse(response);
                
                // Create and save WorldMap entity
                WorldMap worldMap = new WorldMap();
                worldMap.setWorldId(worldId);
                worldMap.setName(world.getName() + " Map");
                worldMap.setDescription(description);
                worldMap.setMapData(mapData);
                worldMap.setWidth(width);
                worldMap.setHeight(height);
                
                // Set background from the generated map data if available
                if (mapData.containsKey("maps") && ((List<?>) mapData.get("maps")).size() > 0) {
                    Map<?, ?> firstMap = (Map<?, ?>) ((List<?>) mapData.get("maps")).get(0);
                    if (firstMap.containsKey("background")) {
                        worldMap.setBackground((String) firstMap.get("background"));
                    } else {
                        worldMap.setBackground("#e6d6a9"); // Default background
                    }
                } else {
                    worldMap.setBackground("#e6d6a9"); // Default background
                }
                
                LocalDateTime now = LocalDateTime.now();
                worldMap.setCreatedAt(now);
                worldMap.setUpdatedAt(now);
                
                save(worldMap);
                
                log.info("Map generated successfully with id: {}", worldMap.getId());
                return worldMap;
            } catch (Exception e) {
                log.error("Error processing LLM response: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate map: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public String toPrompt(WorldMap worldMap) {
        if (worldMap == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Map name: ").append(worldMap.getName()).append("\n");
        sb.append("Map description: ").append(worldMap.getDescription()).append("\n");
        sb.append("Map dimensions: ").append(worldMap.getWidth()).append("x").append(worldMap.getHeight()).append("\n");
        
        if (worldMap.getMapData() != null && !worldMap.getMapData().isEmpty()) {
            // Add summary of map features
            try {
                Map<String, Object> mapData = worldMap.getMapData();
                if (mapData.containsKey("maps") && ((List<?>) mapData.get("maps")).size() > 0) {
                    Map<?, ?> firstMap = (Map<?, ?>) ((List<?>) mapData.get("maps")).get(0);
                    
                    // Count regions
                    if (firstMap.containsKey("regions")) {
                        List<?> regions = (List<?>) firstMap.get("regions");
                        sb.append("Regions: ").append(regions.size()).append("\n");
                    }
                    
                    // Count terrain features
                    if (firstMap.containsKey("terrainFeatures")) {
                        Map<?, ?> terrainFeatures = (Map<?, ?>) firstMap.get("terrainFeatures");
                        
                        if (terrainFeatures.containsKey("waters")) {
                            List<?> waters = (List<?>) terrainFeatures.get("waters");
                            sb.append("Water bodies: ").append(waters.size()).append("\n");
                        }
                        
                        if (terrainFeatures.containsKey("forests")) {
                            List<?> forests = (List<?>) terrainFeatures.get("forests");
                            sb.append("Forests: ").append(forests.size()).append("\n");
                        }
                        
                        if (terrainFeatures.containsKey("mountains")) {
                            List<?> mountains = (List<?>) terrainFeatures.get("mountains");
                            sb.append("Mountain ranges: ").append(mountains.size()).append("\n");
                        }
                        
                        if (terrainFeatures.containsKey("deserts")) {
                            List<?> deserts = (List<?>) terrainFeatures.get("deserts");
                            sb.append("Desert areas: ").append(deserts.size()).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error generating map prompt: {}", e.getMessage());
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Parse JSON response from LLM, handling potential JSON formatting issues
     */
    private Map<String, Object> parseJsonResponse(String response) throws JsonProcessingException {
        // Clean up potential non-JSON content
        String jsonContent = response.trim();
        
        // If response contains markdown code blocks, extract the JSON
        if (jsonContent.contains("```json") && jsonContent.contains("```")) {
            jsonContent = jsonContent.split("```json")[1].split("```")[0].trim();
        } else if (jsonContent.contains("```") && jsonContent.contains("```")) {
            jsonContent = jsonContent.split("```")[1].split("```")[0].trim();
        }
        
        // Parse JSON to Map
        try {
            return objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON: {}", jsonContent, e);
            throw e;
        }
    }
} 