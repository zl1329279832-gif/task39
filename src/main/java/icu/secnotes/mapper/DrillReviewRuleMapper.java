package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillReviewRule;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DrillReviewRuleMapper {

    @Insert("INSERT INTO drill_review_rule(task_id, auto_approve_threshold, manual_review_below, manual_review_triggers) " +
            "VALUES(#{taskId}, #{autoApproveThreshold}, #{manualReviewBelow}, #{manualReviewTriggers}) " +
            "ON DUPLICATE KEY UPDATE auto_approve_threshold=#{autoApproveThreshold}, " +
            "manual_review_below=#{manualReviewBelow}, manual_review_triggers=#{manualReviewTriggers}, " +
            "update_time=CURRENT_TIMESTAMP")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(DrillReviewRule rule);

    @Select("SELECT * FROM drill_review_rule WHERE task_id = #{taskId}")
    DrillReviewRule findByTaskId(@Param("taskId") Integer taskId);

    @Delete("DELETE FROM drill_review_rule WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
