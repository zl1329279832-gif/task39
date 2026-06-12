package icu.secnotes.service;

import icu.secnotes.pojo.AuditLog;
import java.util.List;

public interface AuditLogService {

    void log(String action, Integer actorId, String actorRole,
             String targetType, Integer targetId, String detail, String ipAddress);

    List<AuditLog> getByTarget(String targetType, Integer targetId);

    List<AuditLog> getByActor(Integer actorId);

    List<AuditLog> getRecent(Integer limit);
}
