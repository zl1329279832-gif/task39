package icu.secnotes.service.impl;

import icu.secnotes.mapper.AuditLogMapper;
import icu.secnotes.pojo.AuditLog;
import icu.secnotes.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    public void log(String action, Integer actorId, String actorRole,
                    String targetType, Integer targetId, String detail, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setActorId(actorId);
        auditLog.setActorRole(actorRole);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setIpAddress(ipAddress);
        auditLogMapper.insert(auditLog);
    }

    @Override
    public List<AuditLog> getByTarget(String targetType, Integer targetId) {
        return auditLogMapper.findByTarget(targetType, targetId);
    }

    @Override
    public List<AuditLog> getByActor(Integer actorId) {
        return auditLogMapper.findByActor(actorId);
    }

    @Override
    public List<AuditLog> getRecent(Integer limit) {
        return auditLogMapper.findRecent(limit != null ? limit : 100);
    }
}
