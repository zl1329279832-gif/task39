package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.service.DrillAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DrillAttemptServiceImpl implements DrillAttemptService {

    private static final int DEDUCTION_PER_HINT = 10;

    @Autowired
    private DrillAttemptMapper drillAttemptMapper;

    @Override
    public DrillAttempt submitAttempt(DrillAttempt attempt, DrillCheckpoint checkpoint) {
        attempt.setAttemptTime(LocalDateTime.now());

        // Determine pass/fail based on mode
        boolean passed;
        if ("DEFENSE".equalsIgnoreCase(checkpoint.getMode())) {
            // DEFENSE mode: always pass (student used secure endpoint)
            passed = true;
        } else {
            // EXPLOIT mode: match evidence against verifyPattern
            passed = verifyExploit(attempt.getEvidence(), checkpoint.getVerifyPattern());
        }
        attempt.setPassed(passed);

        // Calculate score with deductions
        int maxScore = checkpoint.getMaxScore();
        int hintsUsed = attempt.getHintsUsed() != null ? attempt.getHintsUsed() : 0;
        int deduction = hintsUsed * DEDUCTION_PER_HINT;

        List<String> deductionReasons = new ArrayList<>();
        if (deduction > 0) {
            deductionReasons.add("{\"reason\":\"hints_used\",\"count\":" + hintsUsed + ",\"amount\":" + deduction + "}");
        }

        int finalScore = passed ? Math.max(0, maxScore - deduction) : 0;
        attempt.setScore(finalScore);

        if (!deductionReasons.isEmpty()) {
            attempt.setDeductionItems("[" + String.join(",", deductionReasons) + "]");
        }

        // Idempotent upsert: check if attempt already exists
        DrillAttempt existing = drillAttemptMapper.selectByUserAndCheckpoint(
                attempt.getUserId(), attempt.getCheckpointId());
        if (existing != null) {
            drillAttemptMapper.updateByUserAndCheckpoint(attempt);
            attempt.setId(existing.getId());
        } else {
            attempt.setCreateTime(LocalDateTime.now());
            drillAttemptMapper.insert(attempt);
        }

        return attempt;
    }

    @Override
    public DrillAttempt getAttempt(Integer userId, Integer checkpointId) {
        return drillAttemptMapper.selectByUserAndCheckpoint(userId, checkpointId);
    }

    @Override
    public List<DrillAttempt> getAttemptsByTaskAndUser(Integer taskId, Integer userId) {
        return drillAttemptMapper.selectByTaskAndUser(taskId, userId);
    }

    private boolean verifyExploit(String evidence, String verifyPattern) {
        if (evidence == null || evidence.isEmpty()) {
            return false;
        }
        if (verifyPattern == null || verifyPattern.isEmpty()) {
            return !evidence.isEmpty();
        }
        try {
            return Pattern.compile(verifyPattern, Pattern.DOTALL).matcher(evidence).find();
        } catch (Exception e) {
            return evidence.contains(verifyPattern);
        }
    }
}
