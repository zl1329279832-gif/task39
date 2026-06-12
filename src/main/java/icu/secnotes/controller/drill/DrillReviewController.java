package icu.secnotes.controller.drill;

import icu.secnotes.pojo.AuditLog;
import icu.secnotes.pojo.EvidenceRecord;
import icu.secnotes.pojo.ReviewRecord;
import icu.secnotes.pojo.Result;
import icu.secnotes.pojo.dto.ReviewRequest;
import icu.secnotes.service.*;
import icu.secnotes.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 证据复核与审计控制器（管理员）
 * 提供证据复核、评分调整、审计日志查询功能
 */
@Slf4j
@RestController
@RequestMapping("/drill/admin")
public class DrillReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private TaskInstanceService instanceService;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 查看待复核的证据列表
     */
    @GetMapping("/review/pending/{taskId}")
    public Result getPendingReview(@PathVariable Integer taskId) {
        List<EvidenceRecord> pending = evidenceService.getPendingReview(taskId);
        return Result.success(pending);
    }

    /**
     * 提交复核判定
     */
    @PostMapping("/review")
    public Result submitReview(@RequestBody ReviewRequest request,
                               HttpServletRequest httpRequest) {
        Integer reviewerId = getCurrentUserId(httpRequest);
        if (reviewerId == null) {
            return Result.error("无效的用户身份");
        }
        try {
            ReviewRecord review = reviewService.review(reviewerId, request);

            // 审计日志
            auditLogService.log("REVIEW", reviewerId, "admin",
                    "EVIDENCE", request.getEvidenceId(),
                    String.format("{\"newJudgment\":\"%s\",\"scoreAdjustment\":%d}",
                            request.getNewJudgment(),
                            request.getScoreAdjustment() != null ? request.getScoreAdjustment() : 0),
                    getClientIp(httpRequest));

            return Result.success(review);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看任务的复核历史
     */
    @GetMapping("/review/history/{taskId}")
    public Result getReviewHistory(@PathVariable Integer taskId) {
        List<ReviewRecord> reviews = reviewService.getReviewsByTask(taskId);
        return Result.success(reviews);
    }

    /**
     * 查看任务的所有实例
     */
    @GetMapping("/instances/{taskId}")
    public Result getTaskInstances(@PathVariable Integer taskId) {
        return Result.success(instanceService.getInstancesByTask(taskId));
    }

    /**
     * 查看实例的评分明细
     */
    @GetMapping("/instance/{instanceId}/scores")
    public Result getInstanceScores(@PathVariable Integer instanceId) {
        try {
            Map<String, Object> summary = evidenceService.getInstanceScoreSummary(instanceId);
            return Result.success(summary);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看实例的证据记录
     */
    @GetMapping("/instance/{instanceId}/evidence")
    public Result getInstanceEvidence(@PathVariable Integer instanceId) {
        List<EvidenceRecord> evidence = evidenceService.getEvidenceByInstance(instanceId);
        return Result.success(evidence);
    }

    /**
     * 查看近期审计日志
     */
    @GetMapping("/audit/recent")
    public Result getRecentAuditLogs(@RequestParam(defaultValue = "50") Integer limit) {
        List<AuditLog> logs = auditLogService.getRecent(limit);
        return Result.success(logs);
    }

    /**
     * 查看任务相关审计日志
     */
    @GetMapping("/audit/task/{taskId}")
    public Result getTaskAuditLogs(@PathVariable Integer taskId) {
        List<AuditLog> logs = auditLogService.getByTarget("TASK", taskId);
        return Result.success(logs);
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

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
