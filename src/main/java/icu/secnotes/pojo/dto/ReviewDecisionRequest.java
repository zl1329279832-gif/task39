package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class ReviewDecisionRequest {
    private Integer ticketId;
    private String decision;
    private Integer overriddenPassed;
    private Integer overriddenScore;
    private String reviewComment;
}
