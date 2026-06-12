package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.mapper.DrillCheckpointMapper;
import icu.secnotes.mapper.DrillReviewRuleMapper;
import icu.secnotes.mapper.DrillReviewTicketMapper;
import icu.secnotes.mapper.DrillScoreSummaryMapper;
import icu.secnotes.mapper.DrillTaskMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillCheckpoint;
import icu.secnotes.pojo.DrillReviewRule;
import icu.secnotes.pojo.DrillReviewTicket;
import icu.secnotes.pojo.DrillScoreSummary;
import icu.secnotes.pojo.DrillTask;
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
import java.time.Duration;
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

    @Autowired
    private DrillTaskMapper taskMapper;

    @Autowired
    private DrillReviewTicketMapper reviewTicketMapper;

    @Autowired
    private DrillReviewRuleMapper reviewRuleMapper;

    @Override
    @Transactional
    public DrillAttempt submit(Integer userId, DrillSubmitRequest request) {
        // 1. 加载检查点
        DrillCheckpoint checkpoint = checkpointMapper.findById(request.getCheckpointId());
        if (checkpoint == null) {
            throw new IllegalArgumentException("检查点不存在: " + request.getCheckpointId());
        }

        // 验证 taskId 一致性，防止跨任务提交
        Integer taskId = request.getTaskId() != null ? request.getTaskId() : checkpoint.getTaskId();
        if (!taskId.equals(checkpoint.getTaskId())) {
            throw new IllegalArgumentException("任务ID与检查点不匹配");
        }

        // 2. 查询已有尝试（按 user + checkpoint + task 隔离）
        DrillAttempt existing = attemptMapper.findByUserAndCheckpointAndTask(
                userId, request.getCheckpointId(), taskId);

        // 3. 幂等检查：已通过 且 版本/模式未变 → 直接返回缓存
        if (existing != null && existing.getPassed() != null && existing.getPassed() == 1) {
            int currentVersion = checkpoint.getVersion() != null ? checkpoint.getVersion() : 1;
            int existingVersion = existing.getCheckpointVersion() != null ? existing.getCheckpointVersion() : 1;
            String currentMode = checkpoint.getMode();
            String existingMode = existing.getCheckpointMode();

            if (currentVersion == existingVersion && Objects.equals(currentMode, existingMode)) {
                log.info("幂等返回: 用户 {} 检查点 {} 已通过 (版本={}, 模式={})",
                        userId, request.getCheckpointId(), currentVersion, currentMode);
                return existing;
            }
            // 版本或模式已变更，旧的通过记录不再有效，需要重新验证
            log.info("检查点配置已变更 (版本: {}→{}, 模式: {}→{}), 需要重新验证",
                    existingVersion, currentVersion, existingMode, currentMode);
        }

        // 4. 检查前置条件
        if (checkpoint.getPrerequisiteId() != null) {
            DrillAttempt prereq = attemptMapper.findByUserAndCheckpointAndTask(
                    userId, checkpoint.getPrerequisiteId(), taskId);
            if (prereq == null || prereq.getPassed() == null || prereq.getPassed() != 1) {
                throw new IllegalStateException("前置检查点尚未通过");
            }
        }

        // 4b. 加载任务规则并执行约束
        DrillTask task = taskMapper.findById(taskId);
        if (task != null && task.getAllowRetry() != null && task.getAllowRetry() == 0) {
            if (existing != null && existing.getPassed() != null && existing.getPassed() == 0
                    && existing.getAttemptNumber() != null && existing.getAttemptNumber() >= 1) {
                throw new IllegalStateException("此任务不允许重试");
            }
        }

        // 5. 验证（EXPLOIT / DEFENSE）
        VerificationResult result;
        if ("EXPLOIT".equalsIgnoreCase(checkpoint.getMode())) {
            result = verifier.verifyExploit(checkpoint, request.getEvidence());
        } else {
            result = verifier.verifyDefense(checkpoint, request.getPayloadSummary());
        }

        // 6. 计算扣分（使用检查点快照值，保证可追溯）
        int baseScore = checkpoint.getMaxScore();
        int maxHints = checkpoint.getMaxHints();
        int timeLimit = checkpoint.getTimeLimit() != null ? checkpoint.getTimeLimit() : 1800;

        // 6. 任务级规则收紧
        if (task != null) {
            if (task.getMaxHintCount() != null) {
                maxHints = Math.min(maxHints, task.getMaxHintCount());
            }
            if (task.getTimeLimitMinutes() != null) {
                timeLimit = Math.min(timeLimit, task.getTimeLimitMinutes() * 60);
            }
        }

        List<Map<String, Object>> deductions = new ArrayList<>();
        int totalDeduction = 0;

        // 6a. 提示次数扣分
        int hintsUsed = request.getHintsUsed() != null ? request.getHintsUsed() : 0;
        if (hintsUsed > maxHints) {
            int d = (hintsUsed - maxHints) * 5;
            Map<String, Object> item = new HashMap<>();
            item.put("reason", "超出提示次数");
            item.put("points", d);
            item.put("hintsUsed", hintsUsed);
            item.put("maxHints", maxHints);
            deductions.add(item);
            totalDeduction += d;
        }

        // 6b. 超时扣分（同时记录客户端和服务端耗时）
        int clientElapsed = request.getElapsedSeconds() != null ? request.getElapsedSeconds() : 0;
        // 服务端耗时：如果有已有尝试，从首次尝试时间计算；否则使用客户端值
        int serverElapsed = clientElapsed;
        if (existing != null && existing.getAttemptTime() != null) {
            long secondsSinceFirst = Duration.between(existing.getAttemptTime(), LocalDateTime.now()).getSeconds();
            serverElapsed = (int) secondsSinceFirst;
        }
        if (clientElapsed > timeLimit) {
            int d = (int) Math.min(20, (double) (clientElapsed - timeLimit) / 60 * 5);
            if (d > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("reason", "超时");
                item.put("points", d);
                item.put("elapsedSeconds", clientElapsed);
                item.put("serverElapsedSeconds", serverElapsed);
                item.put("timeLimit", timeLimit);
                deductions.add(item);
                totalDeduction += d;
            }
        }

        // 6c. 重试扣分（基于尝试序号）
        int attemptNumber = 1;
        if (existing != null) {
            attemptNumber = (existing.getAttemptNumber() != null ? existing.getAttemptNumber() : 1) + 1;
            Map<String, Object> item = new HashMap<>();
            item.put("reason", "重试");
            item.put("points", 3);
            item.put("attemptNumber", attemptNumber);
            deductions.add(item);
            totalDeduction += 3;
        }

        int finalScore = result.isPassed() ? Math.max(0, baseScore - totalDeduction) : 0;

        // 7. 构造或更新尝试记录（含完整检查点快照）
        DrillAttempt attempt = existing != null ? existing : new DrillAttempt();
        attempt.setTaskId(taskId);
        attempt.setCheckpointId(request.getCheckpointId());
        attempt.setUserId(userId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAttemptTime(LocalDateTime.now());
        attempt.setPayloadSummary(request.getPayloadSummary());
        attempt.setEvidence(request.getEvidence());
        attempt.setElapsedSeconds(clientElapsed);
        attempt.setServerElapsedSeconds(serverElapsed);
        attempt.setHintsUsed(hintsUsed);
        attempt.setDeductionItems(toJson(deductions));
        attempt.setScore(finalScore);
        attempt.setPassed(result.isPassed() ? 1 : 0);
        // 检查点配置快照
        attempt.setCheckpointVersion(checkpoint.getVersion() != null ? checkpoint.getVersion() : 1);
        attempt.setCheckpointMode(checkpoint.getMode());
        attempt.setMaxScoreSnapshot(baseScore);
        attempt.setMaxHintsSnapshot(maxHints);
        attempt.setTimeLimitSnapshot(timeLimit);
        attempt.setScreenshotHash(request.getScreenshotHash());
        attempt.setRequestLog(request.getRequestLog());
        attemptMapper.upsert(attempt);

        // 9. 自动创建复核工单
        if (task != null) {
            boolean needReview = false;
            if (task.getEvidenceReviewRequired() != null && task.getEvidenceReviewRequired() == 1) {
                needReview = true;
            }
            DrillReviewRule rule = reviewRuleMapper.findByTaskId(taskId);
            if (rule != null) {
                if (result.isPassed() && finalScore < rule.getManualReviewBelow()) {
                    needReview = true;
                }
                // score >= autoApproveThreshold → auto-approved, no ticket needed
            }
            if (needReview) {
                autoCreateReviewTicketIfNeeded(attempt, taskId, checkpoint);
            }
        }

        // 8. 更新成绩统计
        updateScoreSummary(taskId, userId);

        log.info("提交结果: 用户={} 检查点={} 模式={} 版本={} 通过={} 分数={} 尝试次数={}",
                userId, request.getCheckpointId(), checkpoint.getMode(),
                checkpoint.getVersion(), result.isPassed(), finalScore, attemptNumber);
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

    @Override
    public void recalculateScoreSummary(Integer taskId, Integer userId) {
        updateScoreSummary(taskId, userId);
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

    private void autoCreateReviewTicketIfNeeded(DrillAttempt attempt, Integer taskId, DrillCheckpoint checkpoint) {
        if (reviewTicketMapper.countPendingByAttemptId(attempt.getId()) == 0) {
            DrillReviewTicket ticket = new DrillReviewTicket();
            ticket.setAttemptId(attempt.getId());
            ticket.setTaskId(taskId);
            ticket.setCheckpointId(checkpoint.getId());
            ticket.setUserId(attempt.getUserId());
            ticket.setOriginalPassed(attempt.getPassed());
            ticket.setOriginalScore(attempt.getScore());
            ticket.setReviewStatus("pending");
            reviewTicketMapper.insert(ticket);
            log.info("自动创建复核单: attemptId={}, taskId={}, userId={}", attempt.getId(), taskId, attempt.getUserId());
        }
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
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : item.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    sb.append("\"").append(entry.getValue()).append("\"");
                } else {
                    sb.append(entry.getValue());
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
