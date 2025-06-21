package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.VectorSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 向量同步任务Mapper
 */
@Mapper
public interface VectorSyncTaskMapper extends BaseMapper<VectorSyncTask> {
    
    /**
     * 查询失败的任务
     */
    @Select("SELECT * FROM vector_sync_tasks WHERE status = 'FAILED' AND retry_count < #{maxRetries} ORDER BY created_at ASC LIMIT #{limit}")
    List<VectorSyncTask> selectFailedTasks(@Param("maxRetries") int maxRetries, @Param("limit") int limit);
    
    /**
     * 查询待处理的任务
     */
    @Select("SELECT * FROM vector_sync_tasks WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT #{limit}")
    List<VectorSyncTask> selectPendingTasks(@Param("limit") int limit);
    
    /**
     * 查询超时的处理中任务
     */
    @Select("SELECT * FROM vector_sync_tasks WHERE status = 'PROCESSING' AND updated_at < #{timeoutTime} LIMIT #{limit}")
    List<VectorSyncTask> selectTimeoutProcessingTasks(@Param("timeoutTime") LocalDateTime timeoutTime, @Param("limit") int limit);
    
    /**
     * 更新任务状态
     */
    @Update("UPDATE vector_sync_tasks SET status = #{status}, error_message = #{errorMessage}, " +
            "retry_count = #{retryCount}, updated_at = NOW() WHERE id = #{id}")
    int updateTaskStatus(@Param("id") Long id, @Param("status") String status, 
                        @Param("errorMessage") String errorMessage, @Param("retryCount") Integer retryCount);
    
    /**
     * 标记任务为处理完成
     */
    @Update("UPDATE vector_sync_tasks SET status = 'COMPLETED', processed_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markTaskCompleted(@Param("id") Long id);
    
    /**
     * 标记任务为处理中
     */
    @Update("UPDATE vector_sync_tasks SET status = 'PROCESSING', updated_at = NOW() WHERE id = #{id}")
    int markTaskProcessing(@Param("id") Long id);
    
    /**
     * 删除已完成的旧任务
     */
    @Select("DELETE FROM vector_sync_tasks WHERE status = 'COMPLETED' AND created_at < #{cutoffTime}")
    int deleteCompletedTasksOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
} 