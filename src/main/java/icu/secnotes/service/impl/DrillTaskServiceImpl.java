package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillTaskMapper;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.service.DrillTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DrillTaskServiceImpl implements DrillTaskService {

    @Autowired
    private DrillTaskMapper drillTaskMapper;

    @Autowired
    private DrillCheckpointMapper drillCheckpointMapper;

    @Override
    public DrillTask createTask(DrillTask task, List<DrillCheckpoint> checkpoints) {
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        if (task.getStatus() == null) {
            task.setStatus("active");
        }
        if (task.getDifficulty() == null) {
            task.setDifficulty("medium");
        }
        drillTaskMapper.insert(task);

        for (DrillCheckpoint cp : checkpoints) {
            cp.setTaskId(task.getId());
            cp.setCreateTime(now);
            if (cp.getHttpMethod() == null) {
                cp.setHttpMethod("GET");
            }
            if (cp.getMaxScore() == null) {
                cp.setMaxScore(100);
            }
            if (cp.getMaxHints() == null) {
                cp.setMaxHints(3);
            }
            if (cp.getTimeLimit() == null) {
                cp.setTimeLimit(1800);
            }
            drillCheckpointMapper.insert(cp);
        }

        return task;
    }

    @Override
    public DrillTask getTaskById(Integer id) {
        return drillTaskMapper.selectById(id);
    }

    @Override
    public List<DrillTask> getAllTasks() {
        return drillTaskMapper.selectAll();
    }

    @Override
    public List<DrillTask> getActiveTasks() {
        return drillTaskMapper.selectAllActive();
    }

    @Override
    public boolean updateTask(DrillTask task) {
        task.setUpdateTime(LocalDateTime.now());
        return drillTaskMapper.update(task) > 0;
    }

    @Override
    public boolean archiveTask(Integer id) {
        return drillTaskMapper.archive(id, LocalDateTime.now()) > 0;
    }

    @Override
    public List<DrillCheckpoint> getCheckpointsByTaskId(Integer taskId) {
        return drillCheckpointMapper.selectByTaskId(taskId);
    }
}
