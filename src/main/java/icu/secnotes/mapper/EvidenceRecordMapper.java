package icu.secnotes.mapper;

import icu.secnotes.pojo.EvidenceRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface EvidenceRecordMapper {

    @Insert("INSERT INTO evidence_record(instance_id, task_id, checkpoint_id, user_id, " +
            "evidence_type, content, mode, auto_judgment, admin_judgment, review_id, " +
            "submission_hash, elapsed_seconds, hints_used) " +
            "VALUES(#{instanceId}, #{taskId}, #{checkpointId}, #{userId}, " +
            "#{evidenceType}, #{content}, #{mode}, #{autoJudgment}, #{adminJudgment}, " +
            "#{reviewId}, #{submissionHash}, #{elapsedSeconds}, #{hintsUsed})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvidenceRecord record);

    @Select("SELECT * FROM evidence_record WHERE id = #{id}")
    EvidenceRecord findById(@Param("id") Integer id);

    @Select("SELECT * FROM evidence_record WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId} ORDER BY create_time DESC")
    List<EvidenceRecord> findByInstanceAndCheckpoint(@Param("instanceId") Integer instanceId,
                                                      @Param("checkpointId") Integer checkpointId);

    @Select("SELECT * FROM evidence_record WHERE instance_id = #{instanceId} ORDER BY create_time DESC")
    List<EvidenceRecord> findByInstance(@Param("instanceId") Integer instanceId);

    @Select("SELECT * FROM evidence_record WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId} AND submission_hash = #{hash}")
    EvidenceRecord findByHash(@Param("instanceId") Integer instanceId,
                               @Param("checkpointId") Integer checkpointId,
                               @Param("hash") String hash);

    @Select("SELECT COUNT(*) FROM evidence_record WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId} AND auto_judgment = 'HIT'")
    int countHitByInstanceAndCheckpoint(@Param("instanceId") Integer instanceId,
                                         @Param("checkpointId") Integer checkpointId);

    @Select("SELECT COUNT(*) FROM evidence_record WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId}")
    int countByInstanceAndCheckpoint(@Param("instanceId") Integer instanceId,
                                      @Param("checkpointId") Integer checkpointId);

    @Select("SELECT * FROM evidence_record WHERE instance_id = #{instanceId} " +
            "AND checkpoint_id = #{checkpointId} AND mode = #{mode} ORDER BY create_time DESC")
    List<EvidenceRecord> findByInstanceCheckpointAndMode(@Param("instanceId") Integer instanceId,
                                                          @Param("checkpointId") Integer checkpointId,
                                                          @Param("mode") String mode);

    @Update("UPDATE evidence_record SET admin_judgment=#{adminJudgment}, review_id=#{reviewId} " +
            "WHERE id=#{id}")
    int updateJudgment(@Param("id") Integer id,
                        @Param("adminJudgment") String adminJudgment,
                        @Param("reviewId") Integer reviewId);

    @Select("SELECT * FROM evidence_record WHERE task_id = #{taskId} " +
            "AND auto_judgment = 'HIT' AND admin_judgment IS NULL ORDER BY create_time DESC")
    List<EvidenceRecord> findPendingReview(@Param("taskId") Integer taskId);

    @Delete("DELETE FROM evidence_record WHERE instance_id IN " +
            "(SELECT id FROM drill_task_instance WHERE task_id = #{taskId})")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
