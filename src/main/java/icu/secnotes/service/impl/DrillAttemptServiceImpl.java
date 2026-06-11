package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillScoreSummaryMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillScoreSummary;
import icu.secnotes.pojo.dto.DrillSubmitRequest;
import icu.secnotes.pojo.dto.VerificationResult;
import icu.secnotes.service.CheckpointVerifier;
import icu.secnotes.service.DrillAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DrillAttemptServiceImpl implements DrillAttemptService {

    @Autowired
    private DrillAttemptMapper attemptMapper;

    @Autowired
    private DrillCheckpointMapper checkpointMapper;

    @Autowired
    private DrillScoreSummaryMapper scoreSummaryMapper;

    @Autowired
    private CheckpointVerifier verifier;

    @Override
    @Transactional
    public DrillAttempt submit(Integer userId, DrillSubmitRequest request) {
        // 2. 加载检查点
        DrillCheckpoint checkpoint = checkpointMapper.findById(request.getCheckpointId());
        if (checkpoint == null) {
            throw new IllegalArgumentException("检查点不存在: " + request.getCheckpointId());
        }

        // 确定有效模式：请求指定的模式优先，缺省回退到检查点声明的模式
        String effectiveMode = request.getMode() != null && !request.getMode().trim().isEmpty()
                ? request.getMode().trim().toUpperCase()
                : checkpoint.getMode();

        // 校验模式合法性
        if (!"EXPLOIT".equals(effectiveMode) && !"DEFENSE".equals(effectiveMode)) {
            throw new IllegalArgumentException("非法的提交模式: " + effectiveMode);
        }

        // 1. 幂等检查：按 (userId, checkpointId, mode) 隔离，已通过则直接返回
        DrillAttempt existing = attemptMapper.findByUserAndCheckpointAndMode(
                userId, request.getCheckpointId(), effectiveMode);
        if (existing != null && existing.getPassed() != null && existing.getPassed() == 1) {
            log.info("幂等返回: 用户 {} 检查点 {} 模式 {} 已通过", userId, request.getCheckpointId(), effectiveMode);
            return existing;
        }

        // 3. 检查前置条件（前置检查点必须在同模式下通过）
        if (checkpoint.getPrerequisiteId() != null) {
            DrillAttempt prereq = attemptMapper.findByUserAndCheckpointAndMode(
                    userId, checkpoint.getPrerequisiteId(), effectiveMode);
            if (prereq == null || prereq.getPassed() == null || prereq.getPassed() != 1) {
                throw new IllegalStateException("前置检查点尚未通过");
            }
        }

        // 4. 根据有效模式选择验证策略
        VerificationResult result;
        if ("EXPLOIT".equals(effectiveMode)) {
            result = verifier.verifyExploit(checkpoint, request.getEvidence());
        } else {
            result = verifier.verifyDefense(checkpoint, request.getPayloadSummary());
        }

        // 5. 计算分数和扣分（快照当前满分，防止管理员调整后漂移）
        int baseScore = checkpoint.getMaxScore();
        List<Map<String, Object>> deductions = new ArrayList<>();
        int totalDeduction = 0;

        int hintsUsed = request.getHintsUsed() != null ? request.getHintsUsed() : 0;
        if (hintsUsed > checkpoint.getMaxHints()) {
            int d = (hintsUsed - checkpoint.getMaxHints()) * 5;
            Map<String, Object> item = new HashMap<>();
            item.put("reason", "超出提示次数");
            item.put("points", d);
            deductions.add(item);
            totalDeduction += d;
        }

        int elapsed = request.getElapsedSeconds() != null ? request.getElapsedSeconds() : 0;
        if (checkpoint.getTimeLimit() != null && elapsed > checkpoint.getTimeLimit()) {
            int d = (int) Math.min(20, (double) (elapsed - checkpoint.getTimeLimit()) / 60 * 5);
            if (d > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("reason", "超时");
                item.put("points", d);
                deductions.add(item);
                totalDeduction += d;
            }
        }

        // 重试扣分：仅同模式下的重试才扣分
        if (existing != null) {
            Map<String, Object> item = new HashMap<>();
            item.put("reason", "重试");
            item.put("points", 3);
            deductions.add(item);
            totalDeduction += 3;
        }

        int finalScore = result.isPassed() ? Math.max(0, baseScore - totalDeduction) : 0;

        // 6. 构造或更新尝试记录，写入模式和评分快照
        DrillAttempt attempt = existing != null ? existing : new DrillAttempt();
        attempt.setTaskId(request.getTaskId() != null ? request.getTaskId() : checkpoint.getTaskId());
        attempt.setCheckpointId(request.getCheckpointId());
        attempt.setUserId(userId);
        attempt.setAttemptTime(LocalDateTime.now());
        attempt.setPayloadSummary(request.getPayloadSummary());
        attempt.setEvidence(request.getEvidence());
        attempt.setElapsedSeconds(elapsed);
        attempt.setHintsUsed(hintsUsed);
        attempt.setDeductionItems(toJson(deductions));
        attempt.setMode(effectiveMode);
        attempt.setScoredMaxScore(baseScore);
        attempt.setScoredMode(effectiveMode);
        attempt.setScore(finalScore);
        attempt.setPassed(result.isPassed() ? 1 : 0);
        attemptMapper.upsert(attempt);

        // 7. 更新成绩统计
        updateScoreSummary(attempt.getTaskId(), userId);

        log.info("提交结果: 用户={} 检查点={} 模式={} 通过={} 分数={}",
                userId, request.getCheckpointId(), effectiveMode,
                result.isPassed(), finalScore);
        return attempt;
    }

    @Override
    public List<DrillAttempt> getAttempts(Integer taskId, Integer userId) {
        return attemptMapper.findByTaskAndUser(taskId, userId);
    }

    @Override
    @Transactional
    public void resetAttempts(Integer taskId) {
        attemptMapper.deleteByTaskId(taskId);
    }

    /**
     * 更新成绩统计摘要
     */
    private void updateScoreSummary(Integer taskId, Integer userId) {
        int totalScore = attemptMapper.sumScoreByTaskAndUser(taskId, userId);
        int passedCount = attemptMapper.countPassedByTaskAndUser(taskId, userId);
        int totalCheckpoints = checkpointMapper.countByTaskId(taskId);
        int maxPossible = checkpointMapper.sumMaxScoreByTaskId(taskId);

        BigDecimal pct = totalCheckpoints > 0
                ? BigDecimal.valueOf(passedCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCheckpoints), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        DrillScoreSummary summary = new DrillScoreSummary();
        summary.setTaskId(taskId);
        summary.setUserId(userId);
        summary.setTotalScore(totalScore);
        summary.setMaxPossible(maxPossible);
        summary.setCheckpointsPassed(passedCount);
        summary.setCheckpointsTotal(totalCheckpoints);
        summary.setCompletionPct(pct);
        summary.setLastAttemptTime(LocalDateTime.now());
        scoreSummaryMapper.upsert(summary);
    }

    /**
     * 简易 JSON 序列化（避免引入额外依赖）
     */
    private String toJson(List<Map<String, Object>> deductions) {
        if (deductions == null || deductions.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < deductions.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> item = deductions.get(i);
            sb.append("{\"reason\":\"").append(item.get("reason")).append("\",\"points\":").append(item.get("points")).append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
