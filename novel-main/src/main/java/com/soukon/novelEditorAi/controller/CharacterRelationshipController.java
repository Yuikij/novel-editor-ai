package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/character-relationships")
public class CharacterRelationshipController {

    @Autowired
//    @Qualifier("characterRelationshipServiceImpl")
    private CharacterRelationshipService characterRelationshipServiceImpl;

    @GetMapping
    public Result<List<CharacterRelationship>> list() {
        List<CharacterRelationship> relationships = characterRelationshipServiceImpl.list();
        return Result.success(relationships);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<CharacterRelationship>> listByProjectId(@PathVariable("projectId") Integer projectId) {
        LambdaQueryWrapper<CharacterRelationship> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CharacterRelationship::getProjectId, projectId);
        List<CharacterRelationship> relationships = characterRelationshipServiceImpl.list(queryWrapper);
        return Result.success(relationships);
    }
    
    @GetMapping("/character/{characterId}")
    public Result<List<CharacterRelationship>> listByCharacterId(@PathVariable("characterId") Integer characterId) {
        LambdaQueryWrapper<CharacterRelationship> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CharacterRelationship::getSourceCharacterId, characterId)
                .or()
                .eq(CharacterRelationship::getTargetCharacterId, characterId);
        List<CharacterRelationship> relationships = characterRelationshipServiceImpl.list(queryWrapper);
        return Result.success(relationships);
    }

    @GetMapping("/page")
    public Result<Page<CharacterRelationship>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "projectId", required = false, name = "projectId") Integer projectId,
            @RequestParam(value = "characterId", required = false, name = "characterId") Integer characterId,
            @RequestParam(value = "relationshipType", required = false, name = "relationshipType") String relationshipType) {
        
        Page<CharacterRelationship> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<CharacterRelationship> queryWrapper = new LambdaQueryWrapper<>();
        
        if (projectId != null) {
            queryWrapper.eq(CharacterRelationship::getProjectId, projectId);
        }
        if (characterId != null) {
            queryWrapper.and(wrapper -> wrapper
                    .eq(CharacterRelationship::getSourceCharacterId, characterId)
                    .or()
                    .eq(CharacterRelationship::getTargetCharacterId, characterId));
        }
        if (relationshipType != null && !relationshipType.isEmpty()) {
            queryWrapper.eq(CharacterRelationship::getRelationshipType, relationshipType);
        }
        
        characterRelationshipServiceImpl.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<CharacterRelationship> getById(@PathVariable("id") Integer id) {
        CharacterRelationship relationship = characterRelationshipServiceImpl.getById(id);
        if (relationship != null) {
            return Result.success(relationship);
        }
        return Result.error("Character relationship not found with id: " + id);
    }

    @PostMapping
    public Result<CharacterRelationship> save(@RequestBody CharacterRelationship relationship) {
        LocalDateTime now = LocalDateTime.now();
        relationship.setCreatedAt(now);
        relationship.setUpdatedAt(now);
        characterRelationshipServiceImpl.save(relationship);
        return Result.success("Character relationship created successfully", relationship);
    }

    @PutMapping("/{id}")
    public Result<CharacterRelationship> update(@PathVariable("id") Long id, @RequestBody CharacterRelationship relationship) {
        CharacterRelationship existingRelationship = characterRelationshipServiceImpl.getById(id);
        if (existingRelationship == null) {
            return Result.error("Character relationship not found with id: " + id);
        }
        
        relationship.setId(id);
        relationship.setCreatedAt(existingRelationship.getCreatedAt());
        relationship.setUpdatedAt(LocalDateTime.now());
        characterRelationshipServiceImpl.updateById(relationship);
        
        return Result.success("Character relationship updated successfully", relationship);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        CharacterRelationship relationship = characterRelationshipServiceImpl.getById(id);
        if (relationship == null) {
            return Result.error("Character relationship not found with id: " + id);
        }
        
        characterRelationshipServiceImpl.removeById(id);
        return Result.success("Character relationship deleted successfully", null);
    }
} 