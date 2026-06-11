package icu.secnotes.service;

import icu.secnotes.pojo.DrillScoreSummary;

import java.util.List;

public interface DrillScoreService {

    DrillScoreSummary recalculate(Integer taskId, Integer userId);

    DrillScoreSummary getSummary(Integer taskId, Integer userId);

    List<DrillScoreSummary> getTaskStats(Integer taskId);
}
