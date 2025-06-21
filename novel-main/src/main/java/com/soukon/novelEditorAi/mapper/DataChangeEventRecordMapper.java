package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.DataChangeEventRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据变更事件记录Mapper
 */
@Mapper
public interface DataChangeEventRecordMapper extends BaseMapper<DataChangeEventRecord> {
    
    /**
     * 查询未处理的事件
     */
    @Select("SELECT * FROM data_change_events WHERE processed = false ORDER BY event_time ASC LIMIT #{limit}")
    List<DataChangeEventRecord> selectUnprocessedEvents(@Param("limit") int limit);
    
    /**
     * 标记事件为已处理
     */
    @Update("UPDATE data_change_events SET processed = true, processed_at = NOW() WHERE id = #{id}")
    int markEventProcessed(@Param("id") Long id);
    
    /**
     * 删除旧的已处理事件
     */
    @Select("DELETE FROM data_change_events WHERE processed = true AND event_time < #{cutoffTime}")
    int deleteProcessedEventsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * 查询实体的最新事件
     */
    @Select("SELECT * FROM data_change_events WHERE entity_type = #{entityType} AND entity_id = #{entityId} " +
            "ORDER BY version DESC, event_time DESC LIMIT 1")
    DataChangeEventRecord selectLatestEvent(@Param("entityType") String entityType, @Param("entityId") Long entityId);
} 