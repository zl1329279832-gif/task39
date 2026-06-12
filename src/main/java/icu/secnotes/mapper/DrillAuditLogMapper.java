package icu.secnotes.mapper;

import icu.secnotes.pojo.DrillAuditLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DrillAuditLogMapper {

    @Insert("INSERT INTO drill_audit_log(actor_id, action, target_type, target_id, details, ip_address) " +
            "VALUES(#{actorId}, #{action}, #{targetType}, #{targetId}, #{details}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DrillAuditLog log);

    @Select("SELECT * FROM drill_audit_log WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY create_time DESC")
    List<DrillAuditLog> findByTarget(@Param("targetType") String targetType, @Param("targetId") Integer targetId);

    @Select("SELECT * FROM drill_audit_log WHERE actor_id = #{actorId} ORDER BY create_time DESC")
    List<DrillAuditLog> findByActor(@Param("actorId") Integer actorId);

    @Select("SELECT * FROM drill_audit_log ORDER BY create_time DESC")
    List<DrillAuditLog> findAll();
}
