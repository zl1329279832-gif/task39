package icu.secnotes.controller.drill;

import icu.secnotes.config.CheckpointEndpointRegistry;
import icu.secnotes.pojo.DrillAuditLog;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillReviewRule;
import icu.secnotes.pojo.DrillReviewTicket;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.*;
import icu.secnotes.pojo.Result;
import icu.secnotes.service.DrillAttemptService;
import icu.secnotes.service.DrillAuditService;
import icu.secnotes.service.DrillReviewService;
import icu.secnotes.service.DrillTaskService;
import icu.secnotes.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演练任务管理控制器（管理员）
 * 提供任务创建、更新、归档、检查点管理、成绩统计、复核改判和审计日志功能
 */
@Slf4j
@RestController
@RequestMapping("/drill/admin")
public class DrillTaskAdminController {

    @Autowired
    private DrillTaskService taskService;

    @Autowired
    private DrillAttemptService attemptService;

    @Autowired
    private DrillAuditService auditService;

    @Autowired
    private DrillReviewService reviewService;

    @Autowired
    private CheckpointEndpointRegistry registry;

    /**
     * 创建演练任务（含检查点）
     */
    @PostMapping("/tasks")
    public Result createTask(@RequestBody DrillTaskCreateRequest request,
                             HttpServletRequest httpRequest) {
        Integer creatorId = getCurrentUserId(httpRequest);
        if (creatorId == null) {
            return Result.error("无效的用户身份");
        }
        try {
            DrillTask task = taskService.createTask(request, creatorId);
            safeLog(creatorId, "TASK_CREATE", "TASK", task.getId(),
                    "{\"title\":\"" + task.getTitle() + "\"}", getClientIp(httpRequest));
            Map<String, Object> data = new HashMap<>();
            data.put("task", task);
            data.put("checkpoints", taskService.getCheckpoints(task.getId()));
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有活跃任务
     */
    @GetMapping("/tasks")
    public Result listTasks() {
        return Result.success(taskService.listAllTasks());
    }

    /**
     * 获取任务详情（含检查点）
     */
    @GetMapping("/tasks/{id}")
    public Result getTask(@PathVariable Integer id) {
        DrillTask task = taskService.getTask(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("checkpoints", taskService.getCheckpoints(id));
        return Result.success(data);
    }

    /**
     * 更新任务信息
     */
    @PutMapping("/tasks/{id}")
    public Result updateTask(@PathVariable Integer id, @RequestBody DrillTask task,
                             HttpServletRequest httpRequest) {
        task.setId(id);
        DrillTask updated = taskService.updateTask(task);
        safeLog(getCurrentUserId(httpRequest), "TASK_UPDATE", "TASK", id,
                null, getClientIp(httpRequest));
        return Result.success(updated);
    }

    /**
     * 归档任务
     */
    @DeleteMapping("/tasks/{id}")
    public Result archiveTask(@PathVariable Integer id,
                              HttpServletRequest httpRequest) {
        taskService.archiveTask(id);
        safeLog(getCurrentUserId(httpRequest), "TASK_ARCHIVE", "TASK", id,
                null, getClientIp(httpRequest));
        return Result.success();
    }

    /**
     * 为已有任务添加检查点
     */
    @PostMapping("/tasks/{taskId}/checkpoints")
    public Result addCheckpoint(@PathVariable Integer taskId,
                                @RequestBody DrillCheckpoint checkpoint) {
        checkpoint.setTaskId(taskId);
        try {
            DrillCheckpoint created = taskService.addCheckpoint(checkpoint);
            return Result.success(created);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新检查点
     */
    @PutMapping("/checkpoints/{id}")
    public Result updateCheckpoint(@PathVariable Integer id,
                                   @RequestBody DrillCheckpoint checkpoint,
                                   HttpServletRequest httpRequest) {
        checkpoint.setId(id);
        DrillCheckpoint updated = taskService.updateCheckpoint(checkpoint);
        safeLog(getCurrentUserId(httpRequest), "CHECKPOINT_UPDATE", "CHECKPOINT", id,
                null, getClientIp(httpRequest));
        return Result.success(updated);
    }

    /**
     * 删除检查点
     */
    @DeleteMapping("/checkpoints/{id}")
    public Result deleteCheckpoint(@PathVariable Integer id) {
        taskService.deleteCheckpoint(id);
        return Result.success();
    }

    /**
     * 查看任务成绩统计
     */
    @GetMapping("/tasks/{taskId}/stats")
    public Result getTaskStats(@PathVariable Integer taskId) {
        List<Map<String, Object>> stats = taskService.getTaskScoreStats(taskId);
        return Result.success(stats);
    }

    /**
     * 重置任务的所有尝试记录
     */
    @PostMapping("/tasks/{taskId}/reset")
    public Result resetTask(@PathVariable Integer taskId,
                            HttpServletRequest httpRequest) {
        attemptService.resetAttempts(taskId);
        safeLog(getCurrentUserId(httpRequest), "ATTEMPT_RESET", "TASK", taskId,
                null, getClientIp(httpRequest));
        return Result.success();
    }

    /**
     * 查看可用的漏洞端点注册表
     */
    @GetMapping("/registry")
    public Result getRegistry() {
        return Result.success(registry.getAllEndpoints());
    }

    // ===== 复核工单管理 =====

    @PostMapping("/review/tickets")
    public Result createReviewTicket(@RequestBody ReviewTicketCreateRequest request,
                                      HttpServletRequest httpRequest) {
        Integer adminId = getCurrentUserId(httpRequest);
        if (adminId == null) return Result.error("无效的用户身份");
        try {
            DrillReviewTicket ticket = reviewService.createTicket(
                    request.getAttemptId(), adminId, request.getReason());
            return Result.success(ticket);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/review/tickets")
    public Result listPendingTickets() {
        return Result.success(reviewService.getPendingTickets());
    }

    @GetMapping("/review/tickets/{id}")
    public Result getReviewTicket(@PathVariable Integer id) {
        DrillReviewTicket ticket = reviewService.getTicket(id);
        if (ticket == null) return Result.error("复核单不存在");
        return Result.success(ticket);
    }

    @PostMapping("/review/decide")
    public Result submitReviewDecision(@RequestBody ReviewDecisionRequest request,
                                        HttpServletRequest httpRequest) {
        Integer adminId = getCurrentUserId(httpRequest);
        if (adminId == null) return Result.error("无效的用户身份");
        try {
            DrillReviewTicket ticket = reviewService.submitDecision(
                    request.getTicketId(), adminId, request.getDecision(),
                    request.getOverriddenPassed(), request.getOverriddenScore(),
                    request.getReviewComment(), getClientIp(httpRequest));
            return Result.success(ticket);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/review/batch")
    public Result batchReview(@RequestBody BatchReviewRequest request,
                               HttpServletRequest httpRequest) {
        Integer adminId = getCurrentUserId(httpRequest);
        if (adminId == null) return Result.error("无效的用户身份");
        try {
            List<DrillReviewTicket> tickets = reviewService.batchReview(
                    request.getTaskId(), adminId, request.getDecision(),
                    request.getAttemptIds(), request.getReviewComment(),
                    getClientIp(httpRequest));
            return Result.success(tickets);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}/review-tickets")
    public Result getTaskReviewTickets(@PathVariable Integer taskId) {
        return Result.success(reviewService.getTicketsByTask(taskId));
    }

    // ===== 复核规则 =====

    @PostMapping("/review/rules")
    public Result saveReviewRule(@RequestBody ReviewRuleRequest request,
                                  HttpServletRequest httpRequest) {
        Integer adminId = getCurrentUserId(httpRequest);
        if (adminId == null) return Result.error("无效的用户身份");
        DrillReviewRule rule = reviewService.saveReviewRule(request);
        safeLog(adminId, "REVIEW_RULE_UPDATE", "TASK", request.getTaskId(),
                null, getClientIp(httpRequest));
        return Result.success(rule);
    }

    @GetMapping("/review/rules/{taskId}")
    public Result getReviewRule(@PathVariable Integer taskId) {
        DrillReviewRule rule = reviewService.getReviewRule(taskId);
        if (rule == null) return Result.error("复核规则不存在");
        return Result.success(rule);
    }

    // ===== 审计日志 =====

    @GetMapping("/audit/logs")
    public Result listAuditLogs() {
        return Result.success(auditService.getAllLogs());
    }

    @GetMapping("/audit/logs/{targetType}/{targetId}")
    public Result getAuditLogsForTarget(@PathVariable String targetType,
                                         @PathVariable Integer targetId) {
        return Result.success(auditService.getLogsByTarget(targetType, targetId));
    }

    // ===== 辅助方法 =====

    private Integer getCurrentUserId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token == null || token.trim().isEmpty()) {
                return null;
            }
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            return Integer.parseInt(JwtUtils.parseJwt(token).get("id").toString());
        } catch (Exception e) {
            log.warn("解析用户身份失败: {}", e.getMessage());
            return null;
        }
    }

    private void safeLog(Integer actorId, String action, String targetType,
                         Integer targetId, String details, String ipAddress) {
        try {
            auditService.logAction(actorId != null ? actorId : 0, action,
                    targetType, targetId, details, ipAddress);
        } catch (Exception e) {
            log.warn("审计日志记录失败: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
