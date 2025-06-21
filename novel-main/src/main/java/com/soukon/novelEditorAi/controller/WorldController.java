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

    @Autowired
    private com.soukon.novelEditorAi.service.EntitySyncHelper entitySyncHelper;

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
        
        // 初始化向量版本号
        if (world.getVectorVersion() == null) {
            world.setVectorVersion(1L);
        }
        
        worldService.save(world);
        
        // 触发向量同步（异步）
        entitySyncHelper.triggerCreate("world", world.getId(), null, false);
        
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
        
        // 递增向量版本号
        Long currentVersion = existingWorld.getVectorVersion();
        world.setVectorVersion(currentVersion != null ? currentVersion + 1 : 1L);
        
        // 检查内容是否发生变更
        String oldContent = buildWorldContent(existingWorld);
        String newContent = buildWorldContent(world);
        boolean contentChanged = entitySyncHelper.isContentChanged(oldContent, newContent);
        
        worldService.updateById(world);
        
        // 只在内容真正变更时才触发向量同步
        if (contentChanged) {
            entitySyncHelper.triggerUpdate("world", world.getId(), 
                world.getVectorVersion(), null, false);
        }
        
        return Result.success("World updated successfully", world);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        World world = worldService.getById(id);
        if (world == null) {
            return Result.error("World not found with id: " + id);
        }
        
        worldService.removeById(id);
        
        // 触发向量同步删除（紧急处理）
        entitySyncHelper.triggerDelete("world", id, null, true);
        
        return Result.success("World deleted successfully", null);
    }
    
    /**
     * 构建世界观内容用于向量化比较
     */
    private String buildWorldContent(World world) {
        if (world == null) return "";
        
        StringBuilder content = new StringBuilder();
        if (world.getName() != null) {
            content.append("名称: ").append(world.getName()).append("\n");
        }
        if (world.getDescription() != null) {
            content.append("描述: ").append(world.getDescription()).append("\n");
        }
        if (world.getElements() != null && !world.getElements().isEmpty()) {
            content.append("元素: ").append(world.getElements()).append("\n");
        }
        if (world.getNotes() != null) {
            content.append("备注: ").append(world.getNotes()).append("\n");
        }
        
        return content.toString();
    }
} 