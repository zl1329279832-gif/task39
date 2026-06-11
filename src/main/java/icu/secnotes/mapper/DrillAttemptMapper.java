package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillAttempt;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DrillAttemptMapper {

    @Insert("INSERT INTO drill_attempt(task_id, checkpoint_id, user_id, attempt_time, payload_summary, evidence, " +
            "elapsed_seconds, hints_used, deduction_items, score, passed, create_time) " +
            "VALUES(#{taskId}, #{checkpointId}, #{userId}, #{attemptTime}, #{payloadSummary}, #{evidence}, " +
            "#{elapsedSeconds}, #{hintsUsed}, #{deductionItems}, #{score}, #{passed}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillAttempt attempt);

    @Update("UPDATE drill_attempt SET attempt_time=#{attemptTime}, payload_summary=#{payloadSummary}, " +
            "evidence=#{evidence}, elapsed_seconds=#{elapsedSeconds}, hints_used=#{hintsUsed}, " +
            "deduction_items=#{deductionItems}, score=#{score}, passed=#{passed} " +
            "WHERE user_id=#{userId} AND checkpoint_id=#{checkpointId}")
    int updateByUserAndCheckpoint(DrillAttempt attempt);

    @Select("SELECT * FROM drill_attempt WHERE user_id = #{userId} AND checkpoint_id = #{checkpointId}")
    DrillAttempt selectByUserAndCheckpoint(@Param("userId") Integer userId, @Param("checkpointId") Integer checkpointId);

    @Select("SELECT * FROM drill_attempt WHERE task_id = #{taskId} AND user_id = #{userId}")
    List<DrillAttempt> selectByTaskAndUser(@Param("taskId") Integer taskId, @Param("userId") Integer userId);

    @Select("SELECT * FROM drill_attempt WHERE task_id = #{taskId}")
    List<DrillAttempt> selectByTaskId(@Param("taskId") Integer taskId);
}
