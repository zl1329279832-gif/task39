package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillCheckpoint;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DrillCheckpointMapper {

    @Insert("INSERT INTO drill_checkpoint(task_id, vuln_category, checkpoint_order, mode, vuln_endpoint, sec_endpoint, " +
            "http_method, target_param, max_score, max_hints, time_limit, prerequisite_id, verify_pattern, " +
            "defense_pattern, hint_content, create_time) " +
            "VALUES(#{taskId}, #{vulnCategory}, #{checkpointOrder}, #{mode}, #{vulnEndpoint}, #{secEndpoint}, " +
            "#{httpMethod}, #{targetParam}, #{maxScore}, #{maxHints}, #{timeLimit}, #{prerequisiteId}, " +
            "#{verifyPattern}, #{defensePattern}, #{hintContent}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillCheckpoint checkpoint);

    @Select("SELECT * FROM drill_checkpoint WHERE task_id = #{taskId} ORDER BY checkpoint_order")
    List<DrillCheckpoint> selectByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM drill_checkpoint WHERE id = #{id}")
    DrillCheckpoint selectById(@Param("id") Integer id);

    @Delete("DELETE FROM drill_checkpoint WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
