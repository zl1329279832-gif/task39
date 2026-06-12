package icu.secnotes.service;

import icu.secnotes.pojo.DrillReviewRule;
import icu.secnotes.pojo.DrillReviewTicket;
import icu.secnotes.pojo.dto.ReviewRuleRequest;
import java.util.List;

public interface DrillReviewService {

    DrillReviewTicket createTicket(Integer attemptId, Integer creatorId, String reason);

    DrillReviewTicket submitDecision(Integer ticketId, Integer reviewerId,
                                      String decision, Integer overriddenPassed,
                                      Integer overriddenScore, String reviewComment,
                                      String ipAddress);

    List<DrillReviewTicket> batchReview(Integer taskId, Integer reviewerId,
                                         String decision, List<Integer> attemptIds,
                                         String reviewComment, String ipAddress);

    DrillReviewTicket getTicket(Integer ticketId);

    List<DrillReviewTicket> getTicketsByTask(Integer taskId);

    List<DrillReviewTicket> getPendingTickets();

    List<DrillReviewTicket> getPendingTicketsByTask(Integer taskId);

    DrillReviewRule saveReviewRule(ReviewRuleRequest request);

    DrillReviewRule getReviewRule(Integer taskId);
}
