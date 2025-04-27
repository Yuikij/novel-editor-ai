package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.service.CharacterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/characters")
public class CharacterController {

    @Autowired
    private CharacterService characterService;

    @GetMapping
    public Result<List<Character>> list() {
        List<Character> characters = characterService.list();
        return Result.success(characters);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<Character>> listByProjectId(@PathVariable("projectId") Integer projectId) {
        LambdaQueryWrapper<Character> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Character::getProjectId, projectId);
        queryWrapper.orderByAsc(Character::getName);
        List<Character> characters = characterService.list(queryWrapper);
        return Result.success(characters);
    }

    @GetMapping("/page")
    public Result<Page<Character>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "projectId", required = false, name = "projectId") Integer projectId,
            @RequestParam(value = "name", required = false, name = "name") String name,
            @RequestParam(value = "role", required = false, name = "role") String role) {
        
        Page<Character> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<Character> queryWrapper = new LambdaQueryWrapper<>();
        
        if (projectId != null) {
            queryWrapper.eq(Character::getProjectId, projectId);
        }
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Character::getName, name);
        }
        if (role != null && !role.isEmpty()) {
            queryWrapper.eq(Character::getRole, role);
        }
        
        queryWrapper.orderByAsc(Character::getName);
        characterService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<Character> getById(@PathVariable("id") Integer id) {
        Character character = characterService.getById(id);
        if (character != null) {
            return Result.success(character);
        }
        return Result.error("Character not found with id: " + id);
    }

    @PostMapping
    public Result<Character> save(@RequestBody Character character) {
        LocalDateTime now = LocalDateTime.now();
        character.setCreatedAt(now);
        character.setUpdatedAt(now);
        characterService.save(character);
        return Result.success("Character created successfully", character);
    }

    @PutMapping("/{id}")
    public Result<Character> update(@PathVariable("id") Long id, @RequestBody Character character) {
        Character existingCharacter = characterService.getById(id);
        if (existingCharacter == null) {
            return Result.error("Character not found with id: " + id);
        }
        
        character.setId(id);
        character.setCreatedAt(existingCharacter.getCreatedAt());
        character.setUpdatedAt(LocalDateTime.now());
        characterService.updateById(character);
        
        return Result.success("Character updated successfully", character);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        Character character = characterService.getById(id);
        if (character == null) {
            return Result.error("Character not found with id: " + id);
        }
        
        characterService.removeById(id);
        return Result.success("Character deleted successfully", null);
    }
} 