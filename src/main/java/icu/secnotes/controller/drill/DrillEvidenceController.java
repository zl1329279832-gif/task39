package icu.secnotes.controller.drill;

import icu.secnotes.pojo.*;
import icu.secnotes.pojo.dto.EvidenceSubmitRequest;
import icu.secnotes.pojo.Result;
import icu.secnotes.service.*;
import icu.secnotes.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 演练证据提交控制器（学员）
 * 提供任务实例管理、证据提交、评分查询功能
 */
@Slf4j
@RestController
@RequestMapping("/drill/student")
public class DrillEvidenceController {

    @Autowired
    private TaskInstanceService instanceService;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 开始任务实例（创建或返回已有实例）
     */
    @PostMapping("/instance/start/{taskId}")
    public Result startInstance(@PathVariable Integer taskId,
                                HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        try {
            DrillTaskInstance instance = instanceService.startInstance(taskId, userId);

            auditLogService.log("INSTANCE_START", userId, getUserRole(httpRequest),
                    "INSTANCE", instance.getId(),
                    String.format("{\"taskId\":%d}", taskId),
                    getClientIp(httpRequest));

            return Result.success(instance);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前任务实例
     */
    @GetMapping("/instance/{taskId}")
    public Result getInstance(@PathVariable Integer taskId,
                              HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        DrillTaskInstance instance = instanceService.getInstanceByTaskAndUser(taskId, userId);
        if (instance == null) {
            return Result.error("尚未开始此任务");
        }
        return Result.success(instance);
    }

    /**
     * 提交证据
     */
    @PostMapping("/evidence/submit")
    public Result submitEvidence(@RequestBody EvidenceSubmitRequest request,
                                 HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        try {
            EvidenceRecord record = evidenceService.submitEvidence(userId, request);

            auditLogService.log("EVIDENCE_SUBMIT", userId, getUserRole(httpRequest),
                    "EVIDENCE", record.getId(),
                    String.format("{\"instanceId\":%d,\"checkpointId\":%d,\"judgment\":\"%s\"}",
                            request.getInstanceId(), request.getCheckpointId(),
                            record.getAutoJudgment()),
                    getClientIp(httpRequest));

            return Result.success(record);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取实例的证据列表
     */
    @GetMapping("/evidence/{instanceId}")
    public Result getEvidence(@PathVariable Integer instanceId,
                              HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        // 权限校验：只能查看自己的证据
        DrillTaskInstance instance = instanceService.getInstance(instanceId);
        if (instance == null || !instance.getUserId().equals(userId)) {
            return Result.error("无权查看此实例");
        }
        List<EvidenceRecord> evidence = evidenceService.getEvidenceByInstance(instanceId);
        return Result.success(evidence);
    }

    /**
     * 获取实例的评分明细
     */
    @GetMapping("/instance/{instanceId}/scores")
    public Result getInstanceScores(@PathVariable Integer instanceId,
                                    HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        DrillTaskInstance instance = instanceService.getInstance(instanceId);
        if (instance == null || !instance.getUserId().equals(userId)) {
            return Result.error("无权查看此实例");
        }
        try {
            Map<String, Object> summary = evidenceService.getInstanceScoreSummary(instanceId);
            return Result.success(summary);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看用户所有任务实例
     */
    @GetMapping("/instances")
    public Result getMyInstances(HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        List<DrillTaskInstance> instances = instanceService.getInstancesByUser(userId);
        return Result.success(instances);
    }

    private Integer getCurrentUserId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token == null || token.trim().isEmpty()) return null;
            if (token.startsWith("Bearer ")) token = token.substring(7);
            return Integer.parseInt(JwtUtils.parseJwt(token).get("id").toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserRole(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token == null || token.trim().isEmpty()) return "guest";
            if (token.startsWith("Bearer ")) token = token.substring(7);
            Map<String, Object> claims = JwtUtils.parseJwt(token);
            return claims.getOrDefault("role", "guest").toString();
        } catch (Exception e) {
            return "guest";
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
