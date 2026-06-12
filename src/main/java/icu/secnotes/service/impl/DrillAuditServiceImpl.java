package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAuditLogMapper;
import icu.secnotes.pojo.DrillAuditLog;
import icu.secnotes.service.DrillAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DrillAuditServiceImpl implements DrillAuditService {

    @Autowired
    private DrillAuditLogMapper auditLogMapper;

    @Override
    public void logAction(Integer actorId, String action, String targetType,
                          Integer targetId, String details, String ipAddress) {
        try {
            DrillAuditLog auditLog = new DrillAuditLog();
            auditLog.setActorId(actorId != null ? actorId : 0);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("审计日志记录失败: {}", e.getMessage());
        }
    }

    @Override
    public List<DrillAuditLog> getLogsByTarget(String targetType, Integer targetId) {
        return auditLogMapper.findByTarget(targetType, targetId);
    }

    @Override
    public List<DrillAuditLog> getAllLogs() {
        return auditLogMapper.findAll();
    }
}
