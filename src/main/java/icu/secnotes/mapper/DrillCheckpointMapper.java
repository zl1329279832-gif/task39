package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillCheckpoint;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DrillCheckpointMapper {

    @Insert("INSERT INTO drill_checkpoint(task_id, vuln_category, checkpoint_order, mode, " +
            "vuln_endpoint, sec_endpoint, http_method, target_param, max_score, max_hints, " +
            "time_limit, prerequisite_id, verify_pattern, defense_pattern, hint_content) " +
            "VALUES(#{taskId}, #{vulnCategory}, #{checkpointOrder}, #{mode}, " +
            "#{vulnEndpoint}, #{secEndpoint}, #{httpMethod}, #{targetParam}, #{maxScore}, #{maxHints}, " +
            "#{timeLimit}, #{prerequisiteId}, #{verifyPattern}, #{defensePattern}, #{hintContent})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillCheckpoint checkpoint);

    @Select("SELECT * FROM drill_checkpoint WHERE task_id = #{taskId} ORDER BY checkpoint_order")
    List<DrillCheckpoint> findByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM drill_checkpoint WHERE id = #{id}")
    DrillCheckpoint findById(@Param("id") Integer id);

    @Update("UPDATE drill_checkpoint SET vuln_category=#{vulnCategory}, mode=#{mode}, " +
            "max_score=#{maxScore}, max_hints=#{maxHints}, time_limit=#{timeLimit}, " +
            "verify_pattern=#{verifyPattern}, defense_pattern=#{defensePattern}, " +
            "hint_content=#{hintContent} WHERE id=#{id}")
    int update(DrillCheckpoint checkpoint);

    @Delete("DELETE FROM drill_checkpoint WHERE id = #{id}")
    int deleteById(@Param("id") Integer id);

    @Delete("DELETE FROM drill_checkpoint WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT COUNT(*) FROM drill_checkpoint WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT COALESCE(SUM(max_score), 0) FROM drill_checkpoint WHERE task_id = #{taskId}")
    int sumMaxScoreByTaskId(@Param("taskId") Integer taskId);
}
