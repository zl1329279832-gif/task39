package icu.secnotes.mapper;

import icu.secnotes.pojo.AuditLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO audit_log(action, actor_id, actor_role, target_type, target_id, " +
            "detail, ip_address) " +
            "VALUES(#{action}, #{actorId}, #{actorRole}, #{targetType}, #{targetId}, " +
            "#{detail}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog log);

    @Select("SELECT * FROM audit_log WHERE target_type = #{targetType} AND target_id = #{targetId} " +
            "ORDER BY create_time DESC")
    List<AuditLog> findByTarget(@Param("targetType") String targetType,
                                 @Param("targetId") Integer targetId);

    @Select("SELECT * FROM audit_log WHERE actor_id = #{actorId} ORDER BY create_time DESC")
    List<AuditLog> findByActor(@Param("actorId") Integer actorId);

    @Select("SELECT * FROM audit_log ORDER BY create_time DESC LIMIT #{limit}")
    List<AuditLog> findRecent(@Param("limit") Integer limit);

    @Delete("DELETE FROM audit_log WHERE target_type = 'TASK' AND target_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Integer taskId);
}
