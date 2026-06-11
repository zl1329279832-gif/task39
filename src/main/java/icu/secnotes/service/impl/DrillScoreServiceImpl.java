package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillScoreSummaryMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillScoreSummary;
import icu.secnotes.service.DrillScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DrillScoreServiceImpl implements DrillScoreService {

    @Autowired
    private DrillScoreSummaryMapper drillScoreSummaryMapper;

    @Autowired
    private DrillCheckpointMapper drillCheckpointMapper;

    @Autowired
    private DrillAttemptMapper drillAttemptMapper;

    @Override
    public DrillScoreSummary recalculate(Integer taskId, Integer userId) {
        List<DrillCheckpoint> checkpoints = drillCheckpointMapper.selectByTaskId(taskId);
        List<DrillAttempt> attempts = drillAttemptMapper.selectByTaskAndUser(taskId, userId);

        int totalScore = 0;
        int maxPossible = 0;
        int checkpointsPassed = 0;
        int checkpointsTotal = checkpoints.size();
        LocalDateTime lastAttemptTime = null;

        for (DrillCheckpoint cp : checkpoints) {
            maxPossible += cp.getMaxScore();
        }

        for (DrillAttempt att : attempts) {
            totalScore += att.getScore();
            if (att.getPassed() != null && att.getPassed()) {
                checkpointsPassed++;
            }
            if (lastAttemptTime == null || (att.getAttemptTime() != null && att.getAttemptTime().isAfter(lastAttemptTime))) {
                lastAttemptTime = att.getAttemptTime();
            }
        }

        BigDecimal completionPct = checkpointsTotal > 0
                ? BigDecimal.valueOf(checkpointsPassed * 100.0 / checkpointsTotal).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();

        DrillScoreSummary existing = drillScoreSummaryMapper.selectByTaskAndUser(taskId, userId);
        if (existing != null) {
            existing.setTotalScore(totalScore);
            existing.setMaxPossible(maxPossible);
            existing.setCheckpointsPassed(checkpointsPassed);
            existing.setCheckpointsTotal(checkpointsTotal);
            existing.setCompletionPct(completionPct);
            existing.setLastAttemptTime(lastAttemptTime);
            existing.setUpdateTime(now);
            drillScoreSummaryMapper.updateByTaskAndUser(existing);
            return existing;
        } else {
            DrillScoreSummary summary = new DrillScoreSummary();
            summary.setTaskId(taskId);
            summary.setUserId(userId);
            summary.setTotalScore(totalScore);
            summary.setMaxPossible(maxPossible);
            summary.setCheckpointsPassed(checkpointsPassed);
            summary.setCheckpointsTotal(checkpointsTotal);
            summary.setCompletionPct(completionPct);
            summary.setLastAttemptTime(lastAttemptTime);
            summary.setUpdateTime(now);
            drillScoreSummaryMapper.insert(summary);
            return summary;
        }
    }

    @Override
    public DrillScoreSummary getSummary(Integer taskId, Integer userId) {
        return drillScoreSummaryMapper.selectByTaskAndUser(taskId, userId);
    }

    @Override
    public List<DrillScoreSummary> getTaskStats(Integer taskId) {
        return drillScoreSummaryMapper.selectByTaskId(taskId);
    }
}
