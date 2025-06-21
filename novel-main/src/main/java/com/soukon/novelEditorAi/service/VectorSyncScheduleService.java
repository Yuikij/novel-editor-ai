package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.entities.VectorSyncTask;
import com.soukon.novelEditorAi.mapper.VectorSyncTaskMapper;
import com.soukon.novelEditorAi.mapper.DataChangeEventRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 向量同步定时任务服务
 * 负责处理定时任务：处理待同步任务、恢复失败任务、清理过期数据等
 */
@Service
@Slf4j
public class VectorSyncScheduleService {

    @Autowired
    private UnifiedVectorSyncService vectorSyncService;
    
    @Autowired
    private VectorSyncTaskMapper taskMapper;
    
    @Autowired
    private DataChangeEventRecordMapper eventRecordMapper;

    @Value("${novel.vector.batch-size:50}")
    private int batchSize;

    @Value("${novel.vector.task-timeout-hours:2}")
    private int taskTimeoutHours;

    /**
     * 处理待同步任务
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000)
    public void processPendingTasks() {
        try {
            List<VectorSyncTask> pendingTasks = taskMapper.selectPendingTasks(batchSize);
            
            if (!pendingTasks.isEmpty()) {
                log.info("开始处理 {} 个待同步任务", pendingTasks.size());
                
                for (VectorSyncTask task : pendingTasks) {
                    try {
                        // 标记任务为处理中
                        taskMapper.markTaskProcessing(task.getId());
                        
                        // 异步处理任务
                        vectorSyncService.syncEntity(
                                task.getEntityType(), 
                                task.getEntityId(), 
                                task.getOperation(), 
                                task.getVersion()
                        );
                        
                        // 标记任务完成
                        taskMapper.markTaskCompleted(task.getId());
                        
                    } catch (Exception e) {
                        log.error("处理同步任务失败: {}", task, e);
                        
                        // 更新失败状态
                        task.setRetryCount(task.getRetryCount() + 1);
                        taskMapper.updateTaskStatus(
                                task.getId(), 
                                "FAILED", 
                                e.getMessage(), 
                                task.getRetryCount()
                        );
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("处理待同步任务时发生错误", e);
        }
    }

    /**
     * 恢复失败的同步任务
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000)
    public void recoverFailedTasks() {
        try {
            log.debug("开始恢复失败的同步任务");
            vectorSyncService.recoverFailedTasks();
        } catch (Exception e) {
            log.error("恢复失败任务时发生错误", e);
        }
    }

    /**
     * 处理超时的任务
     * 每10分钟执行一次
     */
    @Scheduled(fixedDelay = 600000)
    public void handleTimeoutTasks() {
        try {
            LocalDateTime timeoutTime = LocalDateTime.now().minusHours(taskTimeoutHours);
            List<VectorSyncTask> timeoutTasks = taskMapper.selectTimeoutProcessingTasks(timeoutTime, batchSize);
            
            if (!timeoutTasks.isEmpty()) {
                log.warn("发现 {} 个超时任务，将重置为PENDING状态", timeoutTasks.size());
                
                for (VectorSyncTask task : timeoutTasks) {
                    taskMapper.updateTaskStatus(
                            task.getId(), 
                            "PENDING", 
                            "任务处理超时，重置为待处理状态", 
                            task.getRetryCount()
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("处理超时任务时发生错误", e);
        }
    }

    /**
     * 清理废弃的向量文档
     * 每小时执行一次
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupDeprecatedDocuments() {
        try {
            log.debug("开始清理废弃的向量文档");
            vectorSyncService.cleanupDeprecatedDocuments();
        } catch (Exception e) {
            log.error("清理废弃文档时发生错误", e);
        }
    }

    /**
     * 清理已完成的旧任务
     * 每天执行一次（凌晨2点）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupCompletedTasks() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7); // 保留7天的已完成任务
            int deletedCount = taskMapper.deleteCompletedTasksOlderThan(cutoffTime);
            
            if (deletedCount > 0) {
                log.info("清理了 {} 个已完成的旧任务", deletedCount);
            }
            
        } catch (Exception e) {
            log.error("清理已完成任务时发生错误", e);
        }
    }

    /**
     * 清理已处理的旧事件
     * 每天执行一次（凌晨3点）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupProcessedEvents() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(3); // 保留3天的已处理事件
            int deletedCount = eventRecordMapper.deleteProcessedEventsOlderThan(cutoffTime);
            
            if (deletedCount > 0) {
                log.info("清理了 {} 个已处理的旧事件", deletedCount);
            }
            
        } catch (Exception e) {
            log.error("清理已处理事件时发生错误", e);
        }
    }

    /**
     * 数据一致性检查
     * 每6小时执行一次
     */
    @Scheduled(fixedDelay = 21600000)
    public void checkDataConsistency() {
        try {
            log.info("开始数据一致性检查");
            
            // 这里可以添加具体的一致性检查逻辑
            // 例如：检查MySQL和向量数据库的数据是否同步
            // 检查是否有遗漏的同步任务等
            
            log.info("数据一致性检查完成");
            
        } catch (Exception e) {
            log.error("数据一致性检查时发生错误", e);
        }
    }
} 