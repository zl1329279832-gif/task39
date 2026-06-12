package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillReviewTicket;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DrillReviewTicketMapper {

    @Insert("INSERT INTO drill_review_ticket(attempt_id, task_id, checkpoint_id, user_id, " +
            "reviewer_id, original_passed, original_score, review_status, reason) " +
            "VALUES(#{attemptId}, #{taskId}, #{checkpointId}, #{userId}, " +
            "#{reviewerId}, #{originalPassed}, #{originalScore}, #{reviewStatus}, #{reason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillReviewTicket ticket);

    @Update("UPDATE drill_review_ticket SET review_status=#{reviewStatus}, " +
            "reviewer_id=#{reviewerId}, overridden_passed=#{overriddenPassed}, " +
            "overridden_score=#{overriddenScore}, review_comment=#{reviewComment}, " +
            "review_time=#{reviewTime} WHERE id=#{id}")
    int updateDecision(DrillReviewTicket ticket);

    @Select("SELECT * FROM drill_review_ticket WHERE id = #{id}")
    DrillReviewTicket findById(@Param("id") Integer id);

    @Select("SELECT * FROM drill_review_ticket WHERE attempt_id = #{attemptId} ORDER BY create_time DESC")
    List<DrillReviewTicket> findByAttemptId(@Param("attemptId") Integer attemptId);

    @Select("SELECT * FROM drill_review_ticket WHERE task_id = #{taskId} ORDER BY create_time DESC")
    List<DrillReviewTicket> findByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM drill_review_ticket WHERE review_status = 'pending' ORDER BY create_time ASC")
    List<DrillReviewTicket> findAllPending();

    @Select("SELECT * FROM drill_review_ticket WHERE task_id = #{taskId} AND review_status = 'pending' ORDER BY create_time ASC")
    List<DrillReviewTicket> findPendingByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT COUNT(*) FROM drill_review_ticket WHERE attempt_id = #{attemptId} AND review_status = 'pending'")
    int countPendingByAttemptId(@Param("attemptId") Integer attemptId);
}
