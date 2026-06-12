package icu.secnotes.mapper;

import icu.secnotes.pojo.ReviewRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ReviewRecordMapper {

    @Insert("INSERT INTO review_record(evidence_id, instance_id, task_id, checkpoint_id, " +
            "reviewer_id, original_judgment, new_judgment, reason, score_adjustment) " +
            "VALUES(#{evidenceId}, #{instanceId}, #{taskId}, #{checkpointId}, " +
            "#{reviewerId}, #{originalJudgment}, #{newJudgment}, #{reason}, #{scoreAdjustment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewRecord record);

    @Select("SELECT * FROM review_record WHERE id = #{id}")
    ReviewRecord findById(@Param("id") Integer id);

    @Select("SELECT * FROM review_record WHERE evidence_id = #{evidenceId} ORDER BY create_time DESC")
    List<ReviewRecord> findByEvidenceId(@Param("evidenceId") Integer evidenceId);

    @Select("SELECT * FROM review_record WHERE instance_id = #{instanceId} ORDER BY create_time DESC")
    List<ReviewRecord> findByInstance(@Param("instanceId") Integer instanceId);

    @Select("SELECT * FROM review_record WHERE task_id = #{taskId} ORDER BY create_time DESC")
    List<ReviewRecord> findByTask(@Param("taskId") Integer taskId);

    @Delete("DELETE FROM review_record WHERE instance_id IN " +
            "(SELECT id FROM drill_task_instance WHERE task_id = #{taskId})")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
