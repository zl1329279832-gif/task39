package icu.secnotes.controller.drill;

import icu.secnotes.config.CheckpointEndpointRegistry;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.pojo.Result;
import icu.secnotes.service.DrillAttemptService;
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
 * 提供任务创建、更新、归档、检查点管理和成绩统计功能
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
    private CheckpointEndpointRegistry registry;

    /**
     * 创建演练任务（含检查点）
     */
    @PostMapping("/tasks")
    public Result createTask(@RequestBody DrillTaskCreateRequest request,
                             HttpServletRequest httpRequest) {
        Integer creatorId = getCurrentUserId(httpRequest);
        try {
            DrillTask task = taskService.createTask(request, creatorId);
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
    public Result updateTask(@PathVariable Integer id, @RequestBody DrillTask task) {
        task.setId(id);
        DrillTask updated = taskService.updateTask(task);
        return Result.success(updated);
    }

    /**
     * 归档任务
     */
    @DeleteMapping("/tasks/{id}")
    public Result archiveTask(@PathVariable Integer id) {
        taskService.archiveTask(id);
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
                                   @RequestBody DrillCheckpoint checkpoint) {
        checkpoint.setId(id);
        DrillCheckpoint updated = taskService.updateCheckpoint(checkpoint);
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
    public Result resetTask(@PathVariable Integer taskId) {
        attemptService.resetAttempts(taskId);
        return Result.success();
    }

    /**
     * 查看可用的漏洞端点注册表
     */
    @GetMapping("/registry")
    public Result getRegistry() {
        return Result.success(registry.getAllEndpoints());
    }

    private Integer getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Integer.parseInt(JwtUtils.parseJwt(token).get("id").toString());
    }
}
