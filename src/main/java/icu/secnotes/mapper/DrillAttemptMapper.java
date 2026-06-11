package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillAttempt;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DrillAttemptMapper {

    @Insert("INSERT INTO drill_attempt(task_id, checkpoint_id, user_id, attempt_time, " +
            "payload_summary, evidence, elapsed_seconds, hints_used, deduction_items, score, passed) " +
            "VALUES(#{taskId}, #{checkpointId}, #{userId}, #{attemptTime}, " +
            "#{payloadSummary}, #{evidence}, #{elapsedSeconds}, #{hintsUsed}, " +
            "#{deductionItems}, #{score}, #{passed}) " +
            "ON DUPLICATE KEY UPDATE attempt_time=#{attemptTime}, payload_summary=#{payloadSummary}, " +
            "evidence=#{evidence}, elapsed_seconds=#{elapsedSeconds}, hints_used=#{hintsUsed}, " +
            "deduction_items=#{deductionItems}, score=#{score}, passed=#{passed}")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(DrillAttempt attempt);

    @Select("SELECT * FROM drill_attempt WHERE user_id = #{userId} AND checkpoint_id = #{checkpointId}")
    DrillAttempt findByUserAndCheckpoint(@Param("userId") Integer userId,
                                         @Param("checkpointId") Integer checkpointId);

    @Select("SELECT * FROM drill_attempt WHERE task_id = #{taskId} AND user_id = #{userId} ORDER BY attempt_time")
    List<DrillAttempt> findByTaskAndUser(@Param("taskId") Integer taskId,
                                          @Param("userId") Integer userId);

    @Select("SELECT COALESCE(SUM(score), 0) FROM drill_attempt WHERE task_id=#{taskId} AND user_id=#{userId}")
    int sumScoreByTaskAndUser(@Param("taskId") Integer taskId, @Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM drill_attempt WHERE task_id=#{taskId} AND user_id=#{userId} AND passed=1")
    int countPassedByTaskAndUser(@Param("taskId") Integer taskId, @Param("userId") Integer userId);

    @Delete("DELETE FROM drill_attempt WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT a.user_id, ad.username, ad.name, " +
            "SUM(a.score) as total_score, " +
            "COUNT(CASE WHEN a.passed=1 THEN 1 END) as checkpoints_passed " +
            "FROM drill_attempt a " +
            "JOIN Admin ad ON a.user_id = ad.id " +
            "WHERE a.task_id = #{taskId} " +
            "GROUP BY a.user_id, ad.username, ad.name " +
            "ORDER BY total_score DESC")
    List<Map<String, Object>> getScoreStatsByTask(@Param("taskId") Integer taskId);
}
