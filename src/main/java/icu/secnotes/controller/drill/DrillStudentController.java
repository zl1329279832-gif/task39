package icu.secnotes.controller.drill;

import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillScoreSummary;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.DrillSubmitRequest;
import icu.secnotes.pojo.dto.ScoreSummaryDTO;
import icu.secnotes.pojo.Result;
import icu.secnotes.service.DrillAttemptService;
import icu.secnotes.service.DrillTaskService;
import icu.secnotes.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 演练学员控制器
 * 提供学员端任务列表、任务开始、提交验证和成绩查询功能
 */
@Slf4j
@RestController
@RequestMapping("/drill/student")
public class DrillStudentController {

    @Autowired
    private DrillTaskService taskService;

    @Autowired
    private DrillAttemptService attemptService;

    /**
     * 查看可用的活跃演练任务
     */
    @GetMapping("/tasks")
    public Result listAvailableTasks() {
        List<DrillTask> tasks = taskService.listActiveTasks();
        return Result.success(tasks);
    }

    /**
     * 开始演练任务，获取检查点列表
     * 返回的检查点不包含 verifyPattern 和 defensePattern（防止泄露验证规则）
     */
    @PostMapping("/start/{taskId}")
    public Result startTask(@PathVariable Integer taskId) {
        DrillTask task = taskService.getTask(taskId);
        if (task == null || !"active".equals(task.getStatus())) {
            return Result.error("任务不存在或已归档");
        }

        List<DrillCheckpoint> checkpoints = taskService.getCheckpoints(taskId);
        // 构建安全的检查点视图（隐藏验证模式）
        List<Map<String, Object>> safeCheckpoints = checkpoints.stream().map(cp -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", cp.getId());
            map.put("vulnCategory", cp.getVulnCategory());
            map.put("checkpointOrder", cp.getCheckpointOrder());
            map.put("mode", cp.getMode());
            map.put("vulnEndpoint", cp.getVulnEndpoint());
            map.put("secEndpoint", cp.getSecEndpoint());
            map.put("httpMethod", cp.getHttpMethod());
            map.put("targetParam", cp.getTargetParam());
            map.put("maxScore", cp.getMaxScore());
            map.put("maxHints", cp.getMaxHints());
            map.put("timeLimit", cp.getTimeLimit());
            map.put("hintContent", cp.getHintContent());
            map.put("prerequisiteId", cp.getPrerequisiteId());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", task);
        data.put("checkpoints", safeCheckpoints);
        return Result.success(data);
    }

    /**
     * 提交检查点尝试
     */
    @PostMapping("/submit")
    public Result submitAttempt(@RequestBody DrillSubmitRequest request,
                                HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        try {
            DrillAttempt attempt = attemptService.submit(userId, request);
            return Result.success(attempt);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看某任务的尝试记录
     */
    @GetMapping("/attempts/{taskId}")
    public Result getAttempts(@PathVariable Integer taskId,
                              HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        List<DrillAttempt> attempts = attemptService.getAttempts(taskId, userId);
        return Result.success(attempts);
    }

    /**
     * 查看某任务的成绩详情
     */
    @GetMapping("/scores/{taskId}")
    public Result getTaskScore(@PathVariable Integer taskId,
                               HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        ScoreSummaryDTO summary = taskService.getUserScoreSummary(taskId, userId);
        return Result.success(summary);
    }

    /**
     * 查看当前用户所有任务的成绩摘要
     */
    @GetMapping("/scores")
    public Result getAllScores(HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return Result.error("无效的用户身份");
        }
        List<DrillTask> tasks = taskService.listActiveTasks();
        List<ScoreSummaryDTO> summaries = new ArrayList<>();
        for (DrillTask task : tasks) {
            try {
                summaries.add(taskService.getUserScoreSummary(task.getId(), userId));
            } catch (Exception e) {
                // 跳过异常的任务
            }
        }
        return Result.success(summaries);
    }

    /**
     * 从请求中解析当前用户 ID
     * 正确处理 Bearer 前缀，对解析异常返回 null
     */
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
}
