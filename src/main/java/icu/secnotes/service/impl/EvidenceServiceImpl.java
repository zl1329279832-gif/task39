package icu.secnotes.service.impl;

import icu.secnotes.mapper.*;
import icu.secnotes.pojo.*;
import icu.secnotes.pojo.dto.EvidenceSubmitRequest;
import icu.secnotes.pojo.dto.VerificationResult;
import icu.secnotes.service.CheckpointVerifier;
import icu.secnotes.service.EvidenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class EvidenceServiceImpl implements EvidenceService {

    @Autowired
    private EvidenceRecordMapper evidenceMapper;

    @Autowired
    private DrillTaskInstanceMapper instanceMapper;

    @Autowired
    private DrillCheckpointMapper checkpointMapper;

    @Autowired
    private ScoreDetailMapper scoreDetailMapper;

    @Autowired
    private CheckpointVerifier verifier;

    @Override
    @Transactional
    public EvidenceRecord submitEvidence(Integer userId, EvidenceSubmitRequest request) {
        // 1. 验证任务实例
        DrillTaskInstance instance = instanceMapper.findById(request.getInstanceId());
        if (instance == null) {
            throw new IllegalArgumentException("任务实例不存在");
        }
        if (!instance.getUserId().equals(userId)) {
            throw new SecurityException("无权操作此任务实例");
        }
        if (!"IN_PROGRESS".equals(instance.getStatus())) {
            throw new IllegalStateException("任务实例已结束，无法提交证据");
        }

        // 检查超时（实例级别）
        if (instance.getDeadline() != null && LocalDateTime.now().isAfter(instance.getDeadline())) {
            instance.setStatus("EXPIRED");
            instanceMapper.updateStatus(instance);
            throw new IllegalStateException("任务已超时");
        }

        // 2. 验证检查点
        DrillCheckpoint checkpoint = checkpointMapper.findById(request.getCheckpointId());
        if (checkpoint == null) {
            throw new IllegalArgumentException("检查点不存在");
        }
        if (!checkpoint.getTaskId().equals(instance.getTaskId())) {
            throw new IllegalArgumentException("检查点不属于此任务");
        }

        // 3. 检查前置条件
        if (checkpoint.getPrerequisiteId() != null) {
            ScoreDetail prereqScore = scoreDetailMapper.findByInstanceAndCheckpoint(
                    instance.getId(), checkpoint.getPrerequisiteId());
            if (prereqScore == null || prereqScore.getPassed() != 1) {
                throw new IllegalStateException("前置检查点尚未通过");
            }
        }

        // 4. 计算提交哈希用于去重
        String content = request.getContent();
        String mode = checkpoint.getMode();
        String submissionHash = computeHash(content, mode, request.getCheckpointId());

        // 5. 重复提交检查（幂等）
        EvidenceRecord duplicate = evidenceMapper.findByHash(
                instance.getId(), request.getCheckpointId(), submissionHash);
        if (duplicate != null) {
            log.info("重复提交拦截: instanceId={}, checkpointId={}, hash={}",
                    instance.getId(), request.getCheckpointId(), submissionHash);
            return duplicate;
        }

        // 6. 检查该检查点是否已通过（用于阻止重复加分）
        ScoreDetail existingScore = scoreDetailMapper.findByInstanceAndCheckpoint(
                instance.getId(), request.getCheckpointId());
        boolean alreadyPassed = existingScore != null && existingScore.getPassed() == 1;

        // 7. 自动判定
        String evidenceType = request.getEvidenceType() != null ? request.getEvidenceType() : "PAYLOAD";
        VerificationResult result;
        if ("EXPLOIT".equalsIgnoreCase(mode)) {
            result = verifier.verifyExploit(checkpoint, content);
        } else {
            result = verifier.verifyDefense(checkpoint, content);
        }

        String autoJudgment = result.isPassed() ? "HIT" : "MISS";

        // 8. 保存证据记录
        EvidenceRecord record = buildEvidenceRecord(request, instance, checkpoint, mode, submissionHash, userId);
        record.setAutoJudgment(autoJudgment);
        evidenceMapper.insert(record);

        // 9. 如果命中且尚未通过，计算并更新评分（阻止重复加分）
        if (result.isPassed() && !alreadyPassed) {
            updateScoreOnHit(instance, checkpoint, request);
        } else if (result.isPassed() && alreadyPassed) {
            log.info("检查点已通过，不再重复加分: instanceId={}, checkpointId={}",
                    instance.getId(), request.getCheckpointId());
        }

        log.info("证据提交: userId={}, instanceId={}, checkpointId={}, mode={}, judgment={}",
                userId, instance.getId(), request.getCheckpointId(), mode, autoJudgment);
        return record;
    }

    @Override
    public List<EvidenceRecord> getEvidenceByInstanceAndCheckpoint(Integer instanceId, Integer checkpointId) {
        return evidenceMapper.findByInstanceAndCheckpoint(instanceId, checkpointId);
    }

    @Override
    public List<EvidenceRecord> getEvidenceByInstance(Integer instanceId) {
        return evidenceMapper.findByInstance(instanceId);
    }

    @Override
    public List<EvidenceRecord> getPendingReview(Integer taskId) {
        return evidenceMapper.findPendingReview(taskId);
    }

    @Override
    public ScoreDetail getScoreDetail(Integer instanceId, Integer checkpointId) {
        return scoreDetailMapper.findByInstanceAndCheckpoint(instanceId, checkpointId);
    }

    @Override
    public List<ScoreDetail> getScoreDetails(Integer instanceId) {
        return scoreDetailMapper.findByInstance(instanceId);
    }

    @Override
    public Map<String, Object> getInstanceScoreSummary(Integer instanceId) {
        DrillTaskInstance instance = instanceMapper.findById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("任务实例不存在");
        }

        List<ScoreDetail> details = scoreDetailMapper.findByInstance(instanceId);
        int totalScore = scoreDetailMapper.sumScoreByInstance(instanceId);
        int passedCount = scoreDetailMapper.countPassedByInstance(instanceId);
        int totalCheckpoints = checkpointMapper.countByTaskId(instance.getTaskId());
        int maxPossible = checkpointMapper.sumMaxScoreByTaskId(instance.getTaskId());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("instanceId", instanceId);
        summary.put("taskId", instance.getTaskId());
        summary.put("userId", instance.getUserId());
        summary.put("status", instance.getStatus());
        summary.put("totalScore", totalScore);
        summary.put("maxPossible", maxPossible);
        summary.put("checkpointsPassed", passedCount);
        summary.put("checkpointsTotal", totalCheckpoints);
        summary.put("completionPct", totalCheckpoints > 0
                ? Math.round((double) passedCount / totalCheckpoints * 10000.0) / 100.0 : 0.0);
        summary.put("scoreDetails", details);
        return summary;
    }

    /**
     * 命中时计算评分并保存/更新 ScoreDetail
     */
    private void updateScoreOnHit(DrillTaskInstance instance, DrillCheckpoint checkpoint,
                                   EvidenceSubmitRequest request) {
        int baseScore = checkpoint.getMaxScore();
        int maxHints = checkpoint.getMaxHints();
        int timeLimit = checkpoint.getTimeLimit() != null ? checkpoint.getTimeLimit() : 1800;

        // 提示扣分
        int hintsUsed = request.getHintsUsed() != null ? request.getHintsUsed() : 0;
        int hintDeduction = 0;
        if (hintsUsed > maxHints) {
            hintDeduction = (hintsUsed - maxHints) * 5;
        }

        // 超时扣分
        int elapsed = request.getElapsedSeconds() != null ? request.getElapsedSeconds() : 0;
        int timeDeduction = 0;
        if (elapsed > timeLimit) {
            timeDeduction = (int) Math.min(20, (double) (elapsed - timeLimit) / 60 * 5);
        }

        // 重试扣分（基于已提交证据数量）
        int priorSubmissions = evidenceMapper.countByInstanceAndCheckpoint(
                instance.getId(), checkpoint.getId());
        int retryDeduction = priorSubmissions > 1 ? (priorSubmissions - 1) * 3 : 0;

        int finalScore = Math.max(0, baseScore - hintDeduction - timeDeduction - retryDeduction);

        // 构建快照 JSON
        String snapshotJson = String.format(
                "{\"maxScore\":%d,\"maxHints\":%d,\"timeLimit\":%d,\"version\":%d,\"mode\":\"%s\"}",
                baseScore, maxHints, timeLimit,
                checkpoint.getVersion() != null ? checkpoint.getVersion() : 1,
                checkpoint.getMode());

        ScoreDetail existing = scoreDetailMapper.findByInstanceAndCheckpoint(
                instance.getId(), checkpoint.getId());
        if (existing != null) {
            existing.setBaseScore(baseScore);
            existing.setHintDeduction(hintDeduction);
            existing.setTimeDeduction(timeDeduction);
            existing.setRetryDeduction(retryDeduction);
            existing.setFinalScore(finalScore);
            existing.setPassed(1);
            existing.setSnapshotJson(snapshotJson);
            scoreDetailMapper.update(existing);
        } else {
            ScoreDetail detail = new ScoreDetail();
            detail.setInstanceId(instance.getId());
            detail.setTaskId(instance.getTaskId());
            detail.setCheckpointId(checkpoint.getId());
            detail.setUserId(instance.getUserId());
            detail.setBaseScore(baseScore);
            detail.setHintDeduction(hintDeduction);
            detail.setTimeDeduction(timeDeduction);
            detail.setRetryDeduction(retryDeduction);
            detail.setReviewAdjustment(0);
            detail.setFinalScore(finalScore);
            detail.setPassed(1);
            detail.setSnapshotJson(snapshotJson);
            scoreDetailMapper.insert(detail);
        }

        // 更新实例总分
        int totalScore = scoreDetailMapper.sumScoreByInstance(instance.getId());
        instanceMapper.updateScore(instance.getId(), totalScore);
    }

    private EvidenceRecord buildEvidenceRecord(EvidenceSubmitRequest request,
                                                DrillTaskInstance instance,
                                                DrillCheckpoint checkpoint,
                                                String mode,
                                                String submissionHash,
                                                Integer userId) {
        EvidenceRecord record = new EvidenceRecord();
        record.setInstanceId(instance.getId());
        record.setTaskId(instance.getTaskId());
        record.setCheckpointId(request.getCheckpointId());
        record.setUserId(userId);
        record.setEvidenceType(request.getEvidenceType() != null ? request.getEvidenceType() : "PAYLOAD");
        record.setContent(request.getContent());
        record.setMode(mode);
        record.setSubmissionHash(submissionHash);
        record.setElapsedSeconds(request.getElapsedSeconds() != null ? request.getElapsedSeconds() : 0);
        record.setHintsUsed(request.getHintsUsed() != null ? request.getHintsUsed() : 0);
        return record;
    }

    /**
     * 计算提交哈希（SHA-256），用于去重
     */
    private String computeHash(String content, String mode, Integer checkpointId) {
        try {
            String raw = content + "|" + mode + "|" + checkpointId;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }
}
