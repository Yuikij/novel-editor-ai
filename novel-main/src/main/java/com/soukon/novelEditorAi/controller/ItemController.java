package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Item;
import com.soukon.novelEditorAi.model.item.ItemRequest;
import com.soukon.novelEditorAi.service.ItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 条目管理控制器
 */
@RestController
@RequestMapping("/api/items")
@Slf4j
public class ItemController {

    @Autowired
    private ItemService itemService;

    /**
     * 创建条目
     * @param item 条目信息
     * @return 创建结果
     */
    @PostMapping
    public Result<Item> createItem(@RequestBody Item item) {
        log.info("创建条目请求: {}", item);
        return itemService.createItem(item);
    }

    /**
     * 批量创建条目
     * @param items 条目列表
     * @return 创建结果
     */
    @PostMapping("/batch")
    public Result<List<Item>> batchCreateItems(@RequestBody List<Item> items) {
        log.info("批量创建条目请求, 数量: {}", items.size());
        return itemService.batchCreateItems(items);
    }

    /**
     * 更新条目
     * @param item 条目信息
     * @return 更新结果
     */
    @PutMapping
    public Result<Boolean> updateItem(@RequestBody Item item) {
        log.info("更新条目请求: {}", item);
        return itemService.updateItem(item);
    }

    /**
     * 删除条目
     * @param id 条目ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteItem(@PathVariable Long id) {
        log.info("删除条目请求, ID: {}", id);
        return itemService.deleteItem(id);
    }

    /**
     * 批量删除条目
     * @param request 包含ID列表的请求对象
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<Boolean> batchDeleteItems(@RequestBody ItemRequest request) {
        log.info("批量删除条目请求, IDs: {}", request.getIds());
        return itemService.batchDeleteItems(request.getIds());
    }

    /**
     * 根据ID获取条目
     * @param id 条目ID
     * @return 条目信息
     */
    @GetMapping("/{id}")
    public Result<Item> getItemById(@PathVariable Long id) {
        log.info("查询条目请求, ID: {}", id);
        return itemService.getItemById(id);
    }

    /**
     * 分页查询条目
     * @param page 页码
     * @param size 每页大小
     * @param name 条目名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<Page<Item>> pageItems(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "tag", required = false) String tag) {
        log.info("分页查询条目请求, page: {}, size: {}, name: {}, tag: {}", page, size, name, tag);
        return itemService.pageItems(page, size, name, tag);
    }

    /**
     * 分页查询条目（使用请求体）
     * @param request 请求对象
     * @return 分页结果
     */
    @PostMapping("/search")
    public Result<Page<Item>> searchItems(@RequestBody ItemRequest request) {
        log.info("高级查询条目请求: {}", request);
        Integer page = request.getPage() != null ? request.getPage() : 1;
        Integer size = request.getSize() != null ? request.getSize() : 10;
        return itemService.pageItems(page, size, request.getName(), request.getTags());
    }

    /**
     * 根据标签查询条目
     * @param tag 标签
     * @return 条目列表
     */
    @GetMapping("/tag/{tag}")
    public Result<List<Item>> getItemsByTag(@PathVariable String tag) {
        log.info("根据标签查询条目请求, tag: {}", tag);
        return itemService.getItemsByTag(tag);
    }
} 