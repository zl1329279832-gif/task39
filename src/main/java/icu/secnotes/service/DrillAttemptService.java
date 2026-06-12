package icu.secnotes.service;

import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.dto.DrillSubmitRequest;
import java.util.List;

public interface DrillAttemptService {

    DrillAttempt submit(Integer userId, DrillSubmitRequest request);

    List<DrillAttempt> getAttempts(Integer taskId, Integer userId);

    void resetAttempts(Integer taskId);

    void recalculateScoreSummary(Integer taskId, Integer userId);
}
