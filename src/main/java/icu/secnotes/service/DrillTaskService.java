package icu.secnotes.service;

import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillTask;

import java.util.List;

public interface DrillTaskService {

    DrillTask createTask(DrillTask task, List<DrillCheckpoint> checkpoints);

    DrillTask getTaskById(Integer id);

    List<DrillTask> getAllTasks();

    List<DrillTask> getActiveTasks();

    boolean updateTask(DrillTask task);

    boolean archiveTask(Integer id);

    List<DrillCheckpoint> getCheckpointsByTaskId(Integer taskId);
}
