package icu.secnotes.controller.Drill;

import icu.secnotes.pojo.*;
import icu.secnotes.pojo.dto.DrillSubmitRequest;
import icu.secnotes.service.DrillAttemptService;
import icu.secnotes.service.DrillScoreService;
import icu.secnotes.service.DrillTaskService;
import icu.secnotes.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/drill/student")
public class DrillStudentController {

    @Autowired
    private DrillTaskService drillTaskService;

    @Autowired
    private DrillAttemptService drillAttemptService;

    @Autowired
    private DrillScoreService drillScoreService;

    /**
     * 列出所有活跃任务
     */
    @GetMapping("/tasks")
    public Result listActiveTasks() {
        return Result.success(drillTaskService.getActiveTasks());
    }

    /**
     * 开始/查看任务（返回任务信息和检查点）
     */
    @GetMapping("/start/{taskId}")
    public Result startTask(@PathVariable Integer taskId) {
        DrillTask task = drillTaskService.getTaskById(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        List<DrillCheckpoint> checkpoints = drillTaskService.getCheckpointsByTaskId(taskId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("checkpoints", checkpoints);
        return Result.success(result);
    }

    /**
     * 提交尝试（幂等：重复提交更新已有记录）
     */
    @PostMapping("/submit")
    public Result submitAttempt(@RequestBody DrillSubmitRequest request, HttpServletRequest httpRequest) {
        Integer userId = extractUserId(httpRequest);

        // Validate checkpoint exists
        DrillCheckpoint checkpoint = null;
        List<DrillCheckpoint> checkpoints = drillTaskService.getCheckpointsByTaskId(request.getTaskId());
        for (DrillCheckpoint cp : checkpoints) {
            if (cp.getId().equals(request.getCheckpointId())) {
                checkpoint = cp;
                break;
            }
        }
        if (checkpoint == null) {
            return Result.error("检查点不存在或不属于该任务");
        }

        // Check prerequisite
        if (checkpoint.getPrerequisiteId() != null) {
            DrillAttempt prereqAttempt = drillAttemptService.getAttempt(userId, checkpoint.getPrerequisiteId());
            if (prereqAttempt == null || !prereqAttempt.getPassed()) {
                return Result.error("请先完成前置检查点");
            }
        }

        // Build attempt
        DrillAttempt attempt = new DrillAttempt();
        attempt.setTaskId(request.getTaskId());
        attempt.setCheckpointId(request.getCheckpointId());
        attempt.setUserId(userId);
        attempt.setPayloadSummary(request.getPayloadSummary());
        attempt.setEvidence(request.getEvidence());
        attempt.setElapsedSeconds(request.getElapsedSeconds() != null ? request.getElapsedSeconds() : 0);
        attempt.setHintsUsed(request.getHintsUsed() != null ? request.getHintsUsed() : 0);

        // Submit and evaluate
        DrillAttempt result = drillAttemptService.submitAttempt(attempt, checkpoint);

        // Recalculate score summary
        drillScoreService.recalculate(request.getTaskId(), userId);

        log.info("用户 {} 提交检查点 {} 尝试, passed={}, score={}", userId, request.getCheckpointId(), result.getPassed(), result.getScore());
        return Result.success(result);
    }

    /**
     * 查看个人成绩
     */
    @GetMapping("/scores/{taskId}")
    public Result getScores(@PathVariable Integer taskId, HttpServletRequest httpRequest) {
        Integer userId = extractUserId(httpRequest);
        DrillScoreSummary summary = drillScoreService.getSummary(taskId, userId);
        if (summary == null) {
            // Return empty summary if no attempts yet
            summary = new DrillScoreSummary();
            summary.setTaskId(taskId);
            summary.setUserId(userId);
            summary.setTotalScore(0);
            summary.setMaxPossible(0);
            summary.setCheckpointsPassed(0);
            summary.setCheckpointsTotal(0);
            summary.setCompletionPct(java.math.BigDecimal.ZERO);
        }
        return Result.success(summary);
    }

    private Integer extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return Integer.valueOf(JwtUtils.parseJwt(token).get("id").toString());
    }
}
