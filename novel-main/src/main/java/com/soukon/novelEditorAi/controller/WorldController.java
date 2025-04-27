package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.World;
import com.soukon.novelEditorAi.service.WorldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/worlds")
public class WorldController {

    @Autowired
    private WorldService worldService;

    @GetMapping
    public Result<List<World>> list() {
        List<World> worlds = worldService.list();
        return Result.success(worlds);
    }

    @GetMapping("/page")
    public Result<Page<World>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "name", required = false, name = "name") String name) {
        
        Page<World> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<World> queryWrapper = new LambdaQueryWrapper<>();
        
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(World::getName, name);
        }
        
        queryWrapper.orderByDesc(World::getUpdatedAt);
        worldService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<World> getById(@PathVariable("id") Long id) {
        World world = worldService.getById(id);
        if (world != null) {
            return Result.success(world);
        }
        return Result.error("World not found with id: " + id);
    }

    @PostMapping
    public Result<World> save(@RequestBody World world) {
        LocalDateTime now = LocalDateTime.now();
        world.setCreatedAt(now);
        world.setUpdatedAt(now);
        worldService.save(world);
        return Result.success("World created successfully", world);
    }

    @PutMapping("/{id}")
    public Result<World> update(@PathVariable("id") Long id, @RequestBody World world) {
        World existingWorld = worldService.getById(id);
        if (existingWorld == null) {
            return Result.error("World not found with id: " + id);
        }
        
        world.setId(id);
        world.setCreatedAt(existingWorld.getCreatedAt());
        world.setUpdatedAt(LocalDateTime.now());
        worldService.updateById(world);
        
        return Result.success("World updated successfully", world);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        World world = worldService.getById(id);
        if (world == null) {
            return Result.error("World not found with id: " + id);
        }
        
        worldService.removeById(id);
        return Result.success("World deleted successfully", null);
    }
} 