package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillScoreSummary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DrillScoreSummaryMapper {

    @Insert("INSERT INTO drill_score_summary(task_id, user_id, total_score, max_possible, checkpoints_passed, " +
            "checkpoints_total, completion_pct, last_attempt_time, update_time) " +
            "VALUES(#{taskId}, #{userId}, #{totalScore}, #{maxPossible}, #{checkpointsPassed}, " +
            "#{checkpointsTotal}, #{completionPct}, #{lastAttemptTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillScoreSummary summary);

    @Update("UPDATE drill_score_summary SET total_score=#{totalScore}, max_possible=#{maxPossible}, " +
            "checkpoints_passed=#{checkpointsPassed}, checkpoints_total=#{checkpointsTotal}, " +
            "completion_pct=#{completionPct}, last_attempt_time=#{lastAttemptTime}, update_time=#{updateTime} " +
            "WHERE task_id=#{taskId} AND user_id=#{userId}")
    int updateByTaskAndUser(DrillScoreSummary summary);

    @Select("SELECT * FROM drill_score_summary WHERE task_id = #{taskId} AND user_id = #{userId}")
    DrillScoreSummary selectByTaskAndUser(@Param("taskId") Integer taskId, @Param("userId") Integer userId);

    @Select("SELECT * FROM drill_score_summary WHERE task_id = #{taskId} ORDER BY total_score DESC")
    List<DrillScoreSummary> selectByTaskId(@Param("taskId") Integer taskId);
}
