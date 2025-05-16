package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.WorldMap;
import com.soukon.novelEditorAi.service.WorldMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/worldMaps")
public class WorldMapController {

    @Autowired
    private WorldMapService worldMapService;

    @GetMapping
    public Result<List<WorldMap>> list() {
        List<WorldMap> maps = worldMapService.list();
        return Result.success(maps);
    }

    @GetMapping("/page")
    public Result<Page<WorldMap>> page(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "worldId", required = false) Long worldId) {
        
        Page<WorldMap> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<WorldMap> queryWrapper = new LambdaQueryWrapper<>();
        
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(WorldMap::getName, name);
        }
        
        if (worldId != null) {
            queryWrapper.eq(WorldMap::getWorldId, worldId);
        }
        
        queryWrapper.orderByDesc(WorldMap::getUpdatedAt);
        worldMapService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<WorldMap> getById(@PathVariable("id") Long id) {
        WorldMap worldMap = worldMapService.getById(id);
        if (worldMap != null) {
            return Result.success(worldMap);
        }
        return Result.error("Map not found with id: " + id);
    }

    @PostMapping
    public Result<WorldMap> save(@RequestBody WorldMap worldMap) {
        LocalDateTime now = LocalDateTime.now();
        worldMap.setCreatedAt(now);
        worldMap.setUpdatedAt(now);
        worldMapService.save(worldMap);
        return Result.success("Map created successfully", worldMap);
    }

    @PutMapping("/{id}")
    public Result<WorldMap> update(@PathVariable("id") Long id, @RequestBody WorldMap worldMap) {
        WorldMap existingMap = worldMapService.getById(id);
        if (existingMap == null) {
            return Result.error("Map not found with id: " + id);
        }
        
        worldMap.setId(id);
        worldMap.setCreatedAt(existingMap.getCreatedAt());
        worldMap.setUpdatedAt(LocalDateTime.now());
        worldMapService.updateById(worldMap);
        
        return Result.success("Map updated successfully", worldMap);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        WorldMap worldMap = worldMapService.getById(id);
        if (worldMap == null) {
            return Result.error("Map not found with id: " + id);
        }
        
        worldMapService.removeById(id);
        return Result.success("Map deleted successfully", null);
    }
    
    /**
     * Generate a map based on world data and description using LLM
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Result<WorldMap>> generateMap(
            @RequestParam("worldId") Long worldId,
            @RequestParam("description") String description,
            @RequestParam(value = "width", defaultValue = "800") Integer width,
            @RequestParam(value = "height", defaultValue = "600") Integer height) {
        
        return worldMapService.generateMapWithLlm(worldId, description, width, height)
                .map(worldMap -> Result.success("Map generated successfully", worldMap))
                .onErrorResume(e -> Mono.just(Result.error("Failed to generate map: " + e.getMessage())));
    }
    
    /**
     * Create a map from existing map JSON data
     */
    @PostMapping(value = "/createFromJson", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<WorldMap> createFromJson(
            @RequestParam("worldId") Long worldId,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam(value = "width", defaultValue = "800") Integer width,
            @RequestParam(value = "height", defaultValue = "600") Integer height,
            @RequestBody Map<String, Object> mapData) {
        
        try {
            WorldMap worldMap = new WorldMap();
            worldMap.setWorldId(worldId);
            worldMap.setName(name);
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
            
            worldMapService.save(worldMap);
            
            return Result.success("Map created successfully from JSON data", worldMap);
        } catch (Exception e) {
            return Result.error("Failed to create map from JSON: " + e.getMessage());
        }
    }
    
    /**
     * Get the map data for rendering in the frontend
     */
    @GetMapping("/{id}/mapData")
    public Result<Map<String, Object>> getMapData(@PathVariable("id") Long id) {
        WorldMap worldMap = worldMapService.getById(id);
        if (worldMap != null && worldMap.getMapData() != null) {
            return Result.success(worldMap.getMapData());
        }
        return Result.error("Map data not found for id: " + id);
    }
} 