package icu.secnotes.mapper;

import icu.secnotes.pojo.ScoreDetail;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ScoreDetailMapper {

    @Insert("INSERT INTO score_detail(instance_id, task_id, checkpoint_id, user_id, " +
            "base_score, hint_deduction, time_deduction, retry_deduction, " +
            "review_adjustment, final_score, passed, snapshot_json) " +
            "VALUES(#{instanceId}, #{taskId}, #{checkpointId}, #{userId}, " +
            "#{baseScore}, #{hintDeduction}, #{timeDeduction}, #{retryDeduction}, " +
            "#{reviewAdjustment}, #{finalScore}, #{passed}, #{snapshotJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoreDetail detail);

    @Select("SELECT * FROM score_detail WHERE id = #{id}")
    ScoreDetail findById(@Param("id") Integer id);

    @Select("SELECT * FROM score_detail WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId}")
    ScoreDetail findByInstanceAndCheckpoint(@Param("instanceId") Integer instanceId,
                                             @Param("checkpointId") Integer checkpointId);

    @Select("SELECT * FROM score_detail WHERE instance_id = #{instanceId} ORDER BY checkpoint_id")
    List<ScoreDetail> findByInstance(@Param("instanceId") Integer instanceId);

    @Update("UPDATE score_detail SET base_score=#{baseScore}, hint_deduction=#{hintDeduction}, " +
            "time_deduction=#{timeDeduction}, retry_deduction=#{retryDeduction}, " +
            "review_adjustment=#{reviewAdjustment}, final_score=#{finalScore}, " +
            "passed=#{passed}, update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int update(ScoreDetail detail);

    @Select("SELECT COALESCE(SUM(final_score), 0) FROM score_detail WHERE instance_id = #{instanceId}")
    int sumScoreByInstance(@Param("instanceId") Integer instanceId);

    @Select("SELECT COUNT(*) FROM score_detail WHERE instance_id = #{instanceId} AND passed = 1")
    int countPassedByInstance(@Param("instanceId") Integer instanceId);

    @Delete("DELETE FROM score_detail WHERE instance_id IN " +
            "(SELECT id FROM drill_task_instance WHERE task_id = #{taskId})")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
