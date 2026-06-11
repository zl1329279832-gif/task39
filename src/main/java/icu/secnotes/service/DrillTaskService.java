package icu.secnotes.service;

import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillTask;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.pojo.dto.ScoreSummaryDTO;
import java.util.List;
import java.util.Map;

public interface DrillTaskService {

    DrillTask createTask(DrillTaskCreateRequest request, Integer creatorId);

    DrillTask getTask(Integer taskId);

    List<DrillTask> listActiveTasks();

    List<DrillTask> listAllTasks();

    DrillTask updateTask(DrillTask task);

    void archiveTask(Integer taskId);

    void deleteTask(Integer taskId);

    DrillCheckpoint addCheckpoint(DrillCheckpoint checkpoint);

    List<DrillCheckpoint> getCheckpoints(Integer taskId);

    DrillCheckpoint getCheckpoint(Integer checkpointId);

    DrillCheckpoint updateCheckpoint(DrillCheckpoint checkpoint);

    void deleteCheckpoint(Integer checkpointId);

    List<Map<String, Object>> getTaskScoreStats(Integer taskId);

    ScoreSummaryDTO getUserScoreSummary(Integer taskId, Integer userId);
}
