package icu.secnotes.service;

import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;

import java.util.List;

public interface DrillAttemptService {

    DrillAttempt submitAttempt(DrillAttempt attempt, DrillCheckpoint checkpoint);

    DrillAttempt getAttempt(Integer userId, Integer checkpointId);

    List<DrillAttempt> getAttemptsByTaskAndUser(Integer taskId, Integer userId);
}
