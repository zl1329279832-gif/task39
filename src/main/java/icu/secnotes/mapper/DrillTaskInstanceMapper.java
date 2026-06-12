package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillTaskInstance;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DrillTaskInstanceMapper {

    @Insert("INSERT INTO drill_task_instance(task_id, user_id, status, rules_snapshot, " +
            "start_time, deadline, total_score) " +
            "VALUES(#{taskId}, #{userId}, #{status}, #{rulesSnapshot}, " +
            "#{startTime}, #{deadline}, #{totalScore})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillTaskInstance instance);

    @Select("SELECT * FROM drill_task_instance WHERE id = #{id}")
    DrillTaskInstance findById(@Param("id") Integer id);

    @Select("SELECT * FROM drill_task_instance WHERE task_id = #{taskId} AND user_id = #{userId}")
    DrillTaskInstance findByTaskAndUser(@Param("taskId") Integer taskId, @Param("userId") Integer userId);

    @Select("SELECT * FROM drill_task_instance WHERE task_id = #{taskId} ORDER BY create_time DESC")
    List<DrillTaskInstance> findByTask(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM drill_task_instance WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<DrillTaskInstance> findByUser(@Param("userId") Integer userId);

    @Update("UPDATE drill_task_instance SET status=#{status}, total_score=#{totalScore}, " +
            "update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateStatus(DrillTaskInstance instance);

    @Update("UPDATE drill_task_instance SET total_score=#{totalScore}, " +
            "update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateScore(@Param("id") Integer id, @Param("totalScore") Integer totalScore);

    @Delete("DELETE FROM drill_task_instance WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
