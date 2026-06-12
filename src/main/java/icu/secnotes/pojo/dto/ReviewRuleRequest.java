package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class ReviewRuleRequest {
    private Integer taskId;
    private Integer autoApproveThreshold;
    private Integer manualReviewBelow;
    private String manualReviewTriggers;
}
