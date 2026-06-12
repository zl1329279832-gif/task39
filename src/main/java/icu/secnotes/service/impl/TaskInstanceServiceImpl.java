package icu.secnotes.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillTaskInstanceMapper;
import icu.secnotes.mapper.DrillTaskMapper;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.DrillTaskInstance;
import icu.secnotes.service.TaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class TaskInstanceServiceImpl implements TaskInstanceService {

    @Autowired
    private DrillTaskInstanceMapper instanceMapper;

    @Autowired
    private DrillTaskMapper taskMapper;

    @Autowired
    private DrillCheckpointMapper checkpointMapper;

    @Override
    @Transactional
    public DrillTaskInstance startInstance(Integer taskId, Integer userId) {
        DrillTask task = taskMapper.findById(taskId);
        if (task == null || !"active".equals(task.getStatus())) {
            throw new IllegalArgumentException("任务不存在或已归档");
        }

        // 幂等：如果已有进行中的实例，直接返回
        DrillTaskInstance existing = instanceMapper.findByTaskAndUser(taskId, userId);
        if (existing != null && "IN_PROGRESS".equals(existing.getStatus())) {
            return existing;
        }

        // 构建规则快照
        List<DrillCheckpoint> checkpoints = checkpointMapper.findByTaskId(taskId);
        String rulesSnapshot = buildRulesSnapshot(task, checkpoints);

        // 计算截止时间（取检查点中最大的 timeLimit 之和）
        int totalTimeLimit = checkpoints.stream()
                .mapToInt(cp -> cp.getTimeLimit() != null ? cp.getTimeLimit() : 1800)
                .sum();

        DrillTaskInstance instance = new DrillTaskInstance();
        instance.setTaskId(taskId);
        instance.setUserId(userId);
        instance.setStatus("IN_PROGRESS");
        instance.setRulesSnapshot(rulesSnapshot);
        instance.setStartTime(LocalDateTime.now());
        instance.setDeadline(LocalDateTime.now().plusSeconds(totalTimeLimit));
        instance.setTotalScore(0);
        instanceMapper.insert(instance);

        log.info("创建任务实例: taskId={}, userId={}, instanceId={}", taskId, userId, instance.getId());
        return instance;
    }

    @Override
    public DrillTaskInstance getInstance(Integer instanceId) {
        return instanceMapper.findById(instanceId);
    }

    @Override
    public DrillTaskInstance getInstanceByTaskAndUser(Integer taskId, Integer userId) {
        return instanceMapper.findByTaskAndUser(taskId, userId);
    }

    @Override
    public List<DrillTaskInstance> getInstancesByTask(Integer taskId) {
        return instanceMapper.findByTask(taskId);
    }

    @Override
    public List<DrillTaskInstance> getInstancesByUser(Integer userId) {
        return instanceMapper.findByUser(userId);
    }

    @Override
    @Transactional
    public void completeInstance(Integer instanceId) {
        DrillTaskInstance instance = instanceMapper.findById(instanceId);
        if (instance != null) {
            instance.setStatus("COMPLETED");
            instanceMapper.updateStatus(instance);
        }
    }

    @Override
    @Transactional
    public void expireInstance(Integer instanceId) {
        DrillTaskInstance instance = instanceMapper.findById(instanceId);
        if (instance != null) {
            instance.setStatus("EXPIRED");
            instanceMapper.updateStatus(instance);
        }
    }

    private String buildRulesSnapshot(DrillTask task, List<DrillCheckpoint> checkpoints) {
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("taskId", task.getId());
            snapshot.put("title", task.getTitle());
            snapshot.put("difficulty", task.getDifficulty());
            List<Map<String, Object>> cpList = new ArrayList<>();
            for (DrillCheckpoint cp : checkpoints) {
                Map<String, Object> cpMap = new LinkedHashMap<>();
                cpMap.put("id", cp.getId());
                cpMap.put("vulnCategory", cp.getVulnCategory());
                cpMap.put("checkpointOrder", cp.getCheckpointOrder());
                cpMap.put("mode", cp.getMode());
                cpMap.put("maxScore", cp.getMaxScore());
                cpMap.put("maxHints", cp.getMaxHints());
                cpMap.put("timeLimit", cp.getTimeLimit());
                cpMap.put("version", cp.getVersion());
                cpMap.put("prerequisiteId", cp.getPrerequisiteId());
                cpList.add(cpMap);
            }
            snapshot.put("checkpoints", cpList);
            return om.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("序列化规则快照失败", e);
            return "{}";
        }
    }
}
