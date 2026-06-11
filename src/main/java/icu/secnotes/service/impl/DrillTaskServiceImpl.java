package icu.secnotes.service.impl;

import icu.secnotes.config.CheckpointEndpointRegistry;
import icu.secnotes.config.CheckpointEndpointRegistry.EndpointPair;
import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillScoreSummaryMapper;
import icu.secnotes.mapper.DrillTaskMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillScoreSummary;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.pojo.dto.ScoreSummaryDTO;
import icu.secnotes.service.DrillTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DrillTaskServiceImpl implements DrillTaskService {

    @Autowired
    private DrillTaskMapper taskMapper;

    @Autowired
    private DrillCheckpointMapper checkpointMapper;

    @Autowired
    private DrillAttemptMapper attemptMapper;

    @Autowired
    private DrillScoreSummaryMapper scoreSummaryMapper;

    @Autowired
    private CheckpointEndpointRegistry registry;

    @Override
    @Transactional
    public DrillTask createTask(DrillTaskCreateRequest request, Integer creatorId) {
        DrillTask task = new DrillTask();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : "medium");
        task.setCreatorId(creatorId);
        task.setStatus("active");
        taskMapper.insert(task);

        if (request.getCheckpoints() != null && !request.getCheckpoints().isEmpty()) {
            Map<Integer, Integer> orderToId = new HashMap<>();
            for (DrillTaskCreateRequest.CheckpointDef def : request.getCheckpoints()) {
                EndpointPair pair = registry.getEndpoints(def.getVulnCategory());
                if (pair == null) {
                    throw new IllegalArgumentException("未知的漏洞类别: " + def.getVulnCategory());
                }

                DrillCheckpoint cp = new DrillCheckpoint();
                cp.setTaskId(task.getId());
                cp.setVulnCategory(def.getVulnCategory());
                cp.setCheckpointOrder(def.getCheckpointOrder() != null ? def.getCheckpointOrder() : 0);
                cp.setMode(def.getMode() != null ? def.getMode() : "EXPLOIT");
                cp.setVulnEndpoint(pair.getVulnEndpoint());
                cp.setSecEndpoint(pair.getSecEndpoint());
                cp.setHttpMethod(pair.getHttpMethod());
                cp.setTargetParam(pair.getTargetParam());
                cp.setMaxScore(def.getMaxScore() != null ? def.getMaxScore() : 100);
                cp.setMaxHints(def.getMaxHints() != null ? def.getMaxHints() : 3);
                cp.setTimeLimit(def.getTimeLimit() != null ? def.getTimeLimit() : 1800);
                cp.setVerifyPattern(def.getCustomVerifyPattern() != null
                        ? def.getCustomVerifyPattern() : pair.getDefaultExploitPattern());
                cp.setDefensePattern(def.getCustomDefensePattern() != null
                        ? def.getCustomDefensePattern() : pair.getDefaultDefensePattern());
                cp.setHintContent(def.getHintContent());

                if (def.getPrerequisiteOrder() != null) {
                    cp.setPrerequisiteId(orderToId.get(def.getPrerequisiteOrder()));
                }
                checkpointMapper.insert(cp);
                orderToId.put(cp.getCheckpointOrder(), cp.getId());
            }
        }
        return task;
    }

    @Override
    public DrillTask getTask(Integer taskId) {
        return taskMapper.findById(taskId);
    }

    @Override
    public List<DrillTask> listActiveTasks() {
        return taskMapper.findAllActive();
    }

    @Override
    public List<DrillTask> listAllTasks() {
        return taskMapper.findAll();
    }

    @Override
    public DrillTask updateTask(DrillTask task) {
        taskMapper.update(task);
        return taskMapper.findById(task.getId());
    }

    @Override
    public void archiveTask(Integer taskId) {
        taskMapper.archive(taskId);
    }

    @Override
    @Transactional
    public void deleteTask(Integer taskId) {
        attemptMapper.deleteByTaskId(taskId);
        taskMapper.deleteById(taskId);
    }

    @Override
    public DrillCheckpoint addCheckpoint(DrillCheckpoint checkpoint) {
        EndpointPair pair = registry.getEndpoints(checkpoint.getVulnCategory());
        if (pair == null) {
            throw new IllegalArgumentException("未知的漏洞类别: " + checkpoint.getVulnCategory());
        }
        if (checkpoint.getVulnEndpoint() == null) {
            checkpoint.setVulnEndpoint(pair.getVulnEndpoint());
        }
        if (checkpoint.getSecEndpoint() == null) {
            checkpoint.setSecEndpoint(pair.getSecEndpoint());
        }
        if (checkpoint.getHttpMethod() == null) {
            checkpoint.setHttpMethod(pair.getHttpMethod());
        }
        if (checkpoint.getTargetParam() == null) {
            checkpoint.setTargetParam(pair.getTargetParam());
        }
        if (checkpoint.getVerifyPattern() == null) {
            checkpoint.setVerifyPattern(pair.getDefaultExploitPattern());
        }
        if (checkpoint.getDefensePattern() == null) {
            checkpoint.setDefensePattern(pair.getDefaultDefensePattern());
        }
        if (checkpoint.getMaxScore() == null) {
            checkpoint.setMaxScore(100);
        }
        if (checkpoint.getMaxHints() == null) {
            checkpoint.setMaxHints(3);
        }
        if (checkpoint.getTimeLimit() == null) {
            checkpoint.setTimeLimit(1800);
        }
        checkpointMapper.insert(checkpoint);
        return checkpoint;
    }

    @Override
    public List<DrillCheckpoint> getCheckpoints(Integer taskId) {
        return checkpointMapper.findByTaskId(taskId);
    }

    @Override
    public DrillCheckpoint getCheckpoint(Integer checkpointId) {
        return checkpointMapper.findById(checkpointId);
    }

    @Override
    public DrillCheckpoint updateCheckpoint(DrillCheckpoint checkpoint) {
        checkpointMapper.update(checkpoint);
        return checkpointMapper.findById(checkpoint.getId());
    }

    @Override
    public void deleteCheckpoint(Integer checkpointId) {
        checkpointMapper.deleteById(checkpointId);
    }

    @Override
    public List<Map<String, Object>> getTaskScoreStats(Integer taskId) {
        return attemptMapper.getScoreStatsByTask(taskId);
    }

    @Override
    public ScoreSummaryDTO getUserScoreSummary(Integer taskId, Integer userId) {
        DrillTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        List<DrillCheckpoint> checkpoints = checkpointMapper.findByTaskId(taskId);
        List<DrillAttempt> attempts = attemptMapper.findByTaskAndUser(taskId, userId);
        Map<Integer, DrillAttempt> attemptMap = attempts.stream()
                .collect(Collectors.toMap(DrillAttempt::getCheckpointId, a -> a));

        ScoreSummaryDTO dto = new ScoreSummaryDTO();
        dto.setTaskId(taskId);
        dto.setTaskTitle(task.getTitle());
        dto.setUserId(userId);

        int totalScore = 0;
        int maxPossible = 0;
        int passed = 0;
        List<ScoreSummaryDTO.CheckpointScore> cpScores = new ArrayList<>();

        for (DrillCheckpoint cp : checkpoints) {
            maxPossible += cp.getMaxScore();
            DrillAttempt attempt = attemptMap.get(cp.getId());

            ScoreSummaryDTO.CheckpointScore cs = new ScoreSummaryDTO.CheckpointScore();
            cs.setCheckpointId(cp.getId());
            cs.setVulnCategory(cp.getVulnCategory());
            cs.setMode(cp.getMode());
            cs.setMaxScore(cp.getMaxScore());

            if (attempt != null) {
                cs.setScore(attempt.getScore());
                cs.setPassed(attempt.getPassed() == 1);
                cs.setHintsUsed(attempt.getHintsUsed());
                cs.setDeductionItems(attempt.getDeductionItems());
                totalScore += attempt.getScore();
                if (attempt.getPassed() == 1) {
                    passed++;
                }
            } else {
                cs.setScore(0);
                cs.setPassed(false);
                cs.setHintsUsed(0);
            }
            cpScores.add(cs);
        }

        dto.setTotalScore(totalScore);
        dto.setMaxPossible(maxPossible);
        dto.setCheckpointsPassed(passed);
        dto.setCheckpointsTotal(checkpoints.size());
        dto.setCompletionPct(checkpoints.isEmpty() ? 0.0
                : Math.round((double) passed / checkpoints.size() * 10000.0) / 100.0);
        dto.setCheckpointScores(cpScores);

        return dto;
    }
}
