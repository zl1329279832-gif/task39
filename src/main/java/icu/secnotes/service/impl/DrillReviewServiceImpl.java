package icu.secnotes.service.impl;

import icu.secnotes.mapper.DrillAttemptMapper;
import icu.secnotes.mapper.DrillReviewRuleMapper;
import icu.secnotes.mapper.DrillReviewTicketMapper;
import icu.secnotes.pojo.DrillAttempt;
import icu.secnotes.pojo.DrillReviewRule;
import icu.secnotes.pojo.DrillReviewTicket;
import icu.secnotes.pojo.dto.ReviewRuleRequest;
import icu.secnotes.service.DrillAttemptService;
import icu.secnotes.service.DrillAuditService;
import icu.secnotes.service.DrillReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DrillReviewServiceImpl implements DrillReviewService {

    @Autowired
    private DrillReviewTicketMapper reviewTicketMapper;

    @Autowired
    private DrillReviewRuleMapper reviewRuleMapper;

    @Autowired
    private DrillAttemptMapper attemptMapper;

    @Autowired
    private DrillAttemptService attemptService;

    @Autowired
    private DrillAuditService auditService;

    @Override
    @Transactional
    public DrillReviewTicket createTicket(Integer attemptId, Integer creatorId, String reason) {
        DrillAttempt attempt = attemptMapper.findById(attemptId);
        if (attempt == null) {
            throw new IllegalArgumentException("尝试记录不存在: " + attemptId);
        }

        int pendingCount = reviewTicketMapper.countPendingByAttemptId(attemptId);
        if (pendingCount > 0) {
            throw new IllegalStateException("该提交已有待处理的复核单");
        }

        DrillReviewTicket ticket = new DrillReviewTicket();
        ticket.setAttemptId(attemptId);
        ticket.setTaskId(attempt.getTaskId());
        ticket.setCheckpointId(attempt.getCheckpointId());
        ticket.setUserId(attempt.getUserId());
        ticket.setReviewerId(creatorId);
        ticket.setOriginalPassed(attempt.getPassed());
        ticket.setOriginalScore(attempt.getScore());
        ticket.setReviewStatus("pending");
        ticket.setReason(reason);
        reviewTicketMapper.insert(ticket);

        log.info("创建复核单: attemptId={}, taskId={}, userId={}, creatorId={}",
                attemptId, attempt.getTaskId(), attempt.getUserId(), creatorId);
        return ticket;
    }

    @Override
    @Transactional
    public DrillReviewTicket submitDecision(Integer ticketId, Integer reviewerId,
                                             String decision, Integer overriddenPassed,
                                             Integer overriddenScore, String reviewComment,
                                             String ipAddress) {
        DrillReviewTicket ticket = reviewTicketMapper.findById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("复核单不存在: " + ticketId);
        }
        if (!"pending".equals(ticket.getReviewStatus())) {
            throw new IllegalStateException("复核单已处理，当前状态: " + ticket.getReviewStatus());
        }

        ticket.setReviewerId(reviewerId);
        ticket.setReviewTime(LocalDateTime.now());
        ticket.setReviewComment(reviewComment);

        switch (decision) {
            case "approved":
                ticket.setReviewStatus("approved");
                break;
            case "rejected":
                ticket.setReviewStatus("rejected");
                break;
            case "overridden":
                ticket.setReviewStatus("overridden");
                ticket.setOverriddenPassed(overriddenPassed);
                ticket.setOverriddenScore(overriddenScore);
                // 更新尝试记录的分数和通过状态
                DrillAttempt attempt = attemptMapper.findById(ticket.getAttemptId());
                if (attempt != null) {
                    attempt.setScore(overriddenScore);
                    attempt.setPassed(overriddenPassed);
                    attemptMapper.upsert(attempt);
                    // 重新计算成绩统计
                    attemptService.recalculateScoreSummary(ticket.getTaskId(), ticket.getUserId());
                    log.info("复核改判: attemptId={}, 原分数={}, 新分数={}, 原通过={}, 新通过={}",
                            ticket.getAttemptId(), ticket.getOriginalScore(),
                            overriddenScore, ticket.getOriginalPassed(), overriddenPassed);
                }
                break;
            default:
                throw new IllegalArgumentException("无效的复核决定: " + decision);
        }

        reviewTicketMapper.updateDecision(ticket);

        // 记录审计日志
        String details = "{\"decision\":\"" + decision + "\",\"originalPassed\":" + ticket.getOriginalPassed() +
                ",\"originalScore\":" + ticket.getOriginalScore() +
                (overriddenPassed != null ? ",\"overriddenPassed\":" + overriddenPassed : "") +
                (overriddenScore != null ? ",\"overriddenScore\":" + overriddenScore : "") + "}";
        auditService.logAction(reviewerId, "REVIEW_DECISION", "REVIEW_TICKET",
                ticketId, details, ipAddress);

        return ticket;
    }

    @Override
    @Transactional
    public List<DrillReviewTicket> batchReview(Integer taskId, Integer reviewerId,
                                                String decision, List<Integer> attemptIds,
                                                String reviewComment, String ipAddress) {
        List<DrillReviewTicket> results = new ArrayList<>();
        for (Integer attemptId : attemptIds) {
            try {
                // 检查是否已有待处理的复核单
                int pendingCount = reviewTicketMapper.countPendingByAttemptId(attemptId);
                DrillReviewTicket ticket;
                if (pendingCount == 0) {
                    ticket = createTicket(attemptId, reviewerId, "批量复核");
                } else {
                    List<DrillReviewTicket> pending = reviewTicketMapper.findByAttemptId(attemptId);
                    ticket = pending.get(0); // 最新的待处理工单
                }

                if ("pending".equals(ticket.getReviewStatus())) {
                    ticket = submitDecision(ticket.getId(), reviewerId, decision,
                            null, null, reviewComment, ipAddress);
                }
                results.add(ticket);
            } catch (Exception e) {
                log.warn("批量复核失败: attemptId={}, error={}", attemptId, e.getMessage());
            }
        }
        return results;
    }

    @Override
    public DrillReviewTicket getTicket(Integer ticketId) {
        return reviewTicketMapper.findById(ticketId);
    }

    @Override
    public List<DrillReviewTicket> getTicketsByTask(Integer taskId) {
        return reviewTicketMapper.findByTaskId(taskId);
    }

    @Override
    public List<DrillReviewTicket> getPendingTickets() {
        return reviewTicketMapper.findAllPending();
    }

    @Override
    public List<DrillReviewTicket> getPendingTicketsByTask(Integer taskId) {
        return reviewTicketMapper.findPendingByTaskId(taskId);
    }

    @Override
    public DrillReviewRule saveReviewRule(ReviewRuleRequest request) {
        DrillReviewRule rule = new DrillReviewRule();
        rule.setTaskId(request.getTaskId());
        rule.setAutoApproveThreshold(request.getAutoApproveThreshold() != null
                ? request.getAutoApproveThreshold() : 80);
        rule.setManualReviewBelow(request.getManualReviewBelow() != null
                ? request.getManualReviewBelow() : 50);
        rule.setManualReviewTriggers(request.getManualReviewTriggers());
        reviewRuleMapper.upsert(rule);
        return reviewRuleMapper.findByTaskId(request.getTaskId());
    }

    @Override
    public DrillReviewRule getReviewRule(Integer taskId) {
        return reviewRuleMapper.findByTaskId(taskId);
    }
}
