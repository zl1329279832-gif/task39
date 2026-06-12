package icu.secnotes.service;

import icu.secnotes.pojo.DrillAuditLog;
import java.util.List;

public interface DrillAuditService {

    void logAction(Integer actorId, String action, String targetType,
                   Integer targetId, String details, String ipAddress);

    List<DrillAuditLog> getLogsByTarget(String targetType, Integer targetId);

    List<DrillAuditLog> getAllLogs();
}
