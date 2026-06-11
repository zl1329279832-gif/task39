package icu.secnotes.controller.Drill;

import icu.secnotes.pojo.*;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.service.DrillRegistryService;
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
@RequestMapping("/drill/admin")
public class DrillAdminController {

    @Autowired
    private DrillTaskService drillTaskService;

    @Autowired
    private DrillScoreService drillScoreService;

    @Autowired
    private DrillRegistryService drillRegistryService;

    /**
     * 创建演练任务（含检查点列表）
     */
    @PostMapping("/tasks")
    public Result createTask(@RequestBody DrillTaskCreateRequest request, HttpServletRequest httpRequest) {
        Integer creatorId = extractUserId(httpRequest);

        DrillTask task = new DrillTask();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDifficulty(request.getDifficulty());
        task.setCreatorId(creatorId);

        List<DrillCheckpoint> checkpoints = new ArrayList<>();
        if (request.getCheckpoints() != null) {
            Map<String, Map<String, Object>> registry = drillRegistryService.getRegistry();
            for (DrillTaskCreateRequest.CheckpointDef def : request.getCheckpoints()) {
                DrillCheckpoint cp = new DrillCheckpoint();
                cp.setVulnCategory(def.getVulnCategory());
                cp.setCheckpointOrder(def.getCheckpointOrder() != null ? def.getCheckpointOrder() : 0);
                cp.setMode(def.getMode() != null ? def.getMode() : "EXPLOIT");
                cp.setMaxScore(def.getMaxScore());
                cp.setMaxHints(def.getMaxHints());
                cp.setTimeLimit(def.getTimeLimit());
                cp.setPrerequisiteId(def.getPrerequisiteId());
                cp.setHintContent(def.getHintContent());

                // Fill from registry if not explicitly provided
                Map<String, Object> regEntry = registry.get(def.getVulnCategory());
                cp.setVulnEndpoint(def.getVulnEndpoint() != null ? def.getVulnEndpoint()
                        : (regEntry != null ? (String) regEntry.get("vulnEndpoint") : ""));
                cp.setSecEndpoint(def.getSecEndpoint() != null ? def.getSecEndpoint()
                        : (regEntry != null ? (String) regEntry.get("secEndpoint") : ""));
                cp.setHttpMethod(def.getHttpMethod() != null ? def.getHttpMethod()
                        : (regEntry != null ? (String) regEntry.get("httpMethod") : "GET"));
                cp.setTargetParam(def.getTargetParam() != null ? def.getTargetParam()
                        : (regEntry != null ? (String) regEntry.get("targetParam") : null));
                cp.setVerifyPattern(def.getVerifyPattern() != null ? def.getVerifyPattern()
                        : (regEntry != null ? (String) regEntry.get("verifyPattern") : null));
                cp.setDefensePattern(def.getDefensePattern() != null ? def.getDefensePattern()
                        : (regEntry != null ? (String) regEntry.get("defensePattern") : null));

                checkpoints.add(cp);
            }
        }

        DrillTask created = drillTaskService.createTask(task, checkpoints);
        List<DrillCheckpoint> savedCheckpoints = drillTaskService.getCheckpointsByTaskId(created.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", created);
        result.put("checkpoints", savedCheckpoints);

        log.info("管理员 {} 创建演练任务: {}", creatorId, created.getTitle());
        return Result.success(result);
    }

    /**
     * 列出所有任务
     */
    @GetMapping("/tasks")
    public Result listTasks() {
        return Result.success(drillTaskService.getAllTasks());
    }

    /**
     * 获取任务详情（含检查点）
     */
    @GetMapping("/tasks/{id}")
    public Result getTask(@PathVariable Integer id) {
        DrillTask task = drillTaskService.getTaskById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        List<DrillCheckpoint> checkpoints = drillTaskService.getCheckpointsByTaskId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("checkpoints", checkpoints);
        return Result.success(result);
    }

    /**
     * 更新任务
     */
    @PutMapping("/tasks/{id}")
    public Result updateTask(@PathVariable Integer id, @RequestBody DrillTask task) {
        task.setId(id);
        boolean updated = drillTaskService.updateTask(task);
        return updated ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 归档任务（软删除）
     */
    @DeleteMapping("/tasks/{id}")
    public Result archiveTask(@PathVariable Integer id) {
        boolean archived = drillTaskService.archiveTask(id);
        return archived ? Result.success("归档成功") : Result.error("归档失败");
    }

    /**
     * 查看任务统计（排行榜）
     */
    @GetMapping("/tasks/{id}/stats")
    public Result getTaskStats(@PathVariable Integer id) {
        List<DrillScoreSummary> stats = drillScoreService.getTaskStats(id);
        return Result.success(stats);
    }

    /**
     * 获取漏洞靶点注册表
     */
    @GetMapping("/registry")
    public Result getRegistry() {
        return Result.success(drillRegistryService.getRegistry());
    }

    private Integer extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return Integer.valueOf(JwtUtils.parseJwt(token).get("id").toString());
    }
}
