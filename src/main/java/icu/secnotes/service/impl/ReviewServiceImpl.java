package icu.secnotes.service.impl;

import icu.secnotes.mapper.*;
import icu.secnotes.pojo.*;
import icu.secnotes.pojo.dto.ReviewRequest;
import icu.secnotes.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRecordMapper reviewMapper;

    @Autowired
    private EvidenceRecordMapper evidenceMapper;

    @Autowired
    private ScoreDetailMapper scoreDetailMapper;

    @Autowired
    private DrillTaskInstanceMapper instanceMapper;

    @Autowired
    private DrillCheckpointMapper checkpointMapper;

    @Override
    @Transactional
    public ReviewRecord review(Integer reviewerId, ReviewRequest request) {
        // 1. 加载证据
        EvidenceRecord evidence = evidenceMapper.findById(request.getEvidenceId());
        if (evidence == null) {
            throw new IllegalArgumentException("证据记录不存在");
        }

        String newJudgment = request.getNewJudgment();
        if (!"HIT".equals(newJudgment) && !"MISS".equals(newJudgment)) {
            throw new IllegalArgumentException("判定结果必须为 HIT 或 MISS");
        }

        String originalJudgment = evidence.getAdminJudgment() != null
                ? evidence.getAdminJudgment() : evidence.getAutoJudgment();

        // 2. 创建复核记录
        ReviewRecord review = new ReviewRecord();
        review.setEvidenceId(evidence.getId());
        review.setInstanceId(evidence.getInstanceId());
        review.setTaskId(evidence.getTaskId());
        review.setCheckpointId(evidence.getCheckpointId());
        review.setReviewerId(reviewerId);
        review.setOriginalJudgment(originalJudgment);
        review.setNewJudgment(newJudgment);
        review.setReason(request.getReason());
        review.setScoreAdjustment(request.getScoreAdjustment() != null ? request.getScoreAdjustment() : 0);
        reviewMapper.insert(review);

        // 3. 更新证据的管理员判定
        evidenceMapper.updateJudgment(evidence.getId(), newJudgment, review.getId());

        // 4. 如果判定发生变化，重新计算评分
        if (!newJudgment.equals(originalJudgment)) {
            recalculateScore(evidence, newJudgment, review);
        } else if (request.getScoreAdjustment() != null && request.getScoreAdjustment() != 0) {
            // 判定未变但有分数调整
            applyScoreAdjustment(evidence, request.getScoreAdjustment());
        }

        log.info("管理员复核: reviewerId={}, evidenceId={}, {}→{}, adjustment={}",
                reviewerId, evidence.getId(), originalJudgment, newJudgment,
                review.getScoreAdjustment());
        return review;
    }

    @Override
    public List<ReviewRecord> getReviewsByEvidence(Integer evidenceId) {
        return reviewMapper.findByEvidenceId(evidenceId);
    }

    @Override
    public List<ReviewRecord> getReviewsByInstance(Integer instanceId) {
        return reviewMapper.findByInstance(instanceId);
    }

    @Override
    public List<ReviewRecord> getReviewsByTask(Integer taskId) {
        return reviewMapper.findByTask(taskId);
    }

    /**
     * 判定变更时重新计算评分
     */
    private void recalculateScore(EvidenceRecord evidence, String newJudgment, ReviewRecord review) {
        ScoreDetail detail = scoreDetailMapper.findByInstanceAndCheckpoint(
                evidence.getInstanceId(), evidence.getCheckpointId());

        if ("HIT".equals(newJudgment) && (detail == null || detail.getPassed() != 1)) {
            // MISS -> HIT: 需要给分
            DrillCheckpoint checkpoint = checkpointMapper.findById(evidence.getCheckpointId());
            if (checkpoint == null) return;

            int baseScore = checkpoint.getMaxScore();
            int scoreAdj = review.getScoreAdjustment() != null ? review.getScoreAdjustment() : 0;
            int finalScore = Math.max(0, baseScore + scoreAdj);

            if (detail == null) {
                detail = new ScoreDetail();
                detail.setInstanceId(evidence.getInstanceId());
                detail.setTaskId(evidence.getTaskId());
                detail.setCheckpointId(evidence.getCheckpointId());
                detail.setUserId(evidence.getUserId());
                detail.setBaseScore(baseScore);
                detail.setHintDeduction(0);
                detail.setTimeDeduction(0);
                detail.setRetryDeduction(0);
                detail.setReviewAdjustment(scoreAdj);
                detail.setFinalScore(finalScore);
                detail.setPassed(1);
                detail.setSnapshotJson(String.format("{\"reviewOverride\":true,\"version\":%d}",
                        checkpoint.getVersion() != null ? checkpoint.getVersion() : 1));
                scoreDetailMapper.insert(detail);
            } else {
                detail.setReviewAdjustment(scoreAdj);
                detail.setFinalScore(Math.max(0, detail.getBaseScore()
                        - detail.getHintDeduction() - detail.getTimeDeduction()
                        - detail.getRetryDeduction() + scoreAdj));
                detail.setPassed(1);
                scoreDetailMapper.update(detail);
            }
        } else if ("MISS".equals(newJudgment) && detail != null && detail.getPassed() == 1) {
            // HIT -> MISS: 撤销得分
            int scoreAdj = review.getScoreAdjustment() != null ? review.getScoreAdjustment() : 0;
            detail.setReviewAdjustment(scoreAdj);
            detail.setFinalScore(Math.max(0, scoreAdj));
            detail.setPassed(0);
            scoreDetailMapper.update(detail);
        }

        // 更新实例总分
        int totalScore = scoreDetailMapper.sumScoreByInstance(evidence.getInstanceId());
        instanceMapper.updateScore(evidence.getInstanceId(), totalScore);
    }

    /**
     * 判定未变时的分数微调
     */
    private void applyScoreAdjustment(EvidenceRecord evidence, Integer adjustment) {
        ScoreDetail detail = scoreDetailMapper.findByInstanceAndCheckpoint(
                evidence.getInstanceId(), evidence.getCheckpointId());
        if (detail != null) {
            detail.setReviewAdjustment(detail.getReviewAdjustment() + adjustment);
            detail.setFinalScore(Math.max(0, detail.getBaseScore()
                    - detail.getHintDeduction() - detail.getTimeDeduction()
                    - detail.getRetryDeduction() + detail.getReviewAdjustment()));
            scoreDetailMapper.update(detail);

            int totalScore = scoreDetailMapper.sumScoreByInstance(evidence.getInstanceId());
            instanceMapper.updateScore(evidence.getInstanceId(), totalScore);
        }
    }
}
