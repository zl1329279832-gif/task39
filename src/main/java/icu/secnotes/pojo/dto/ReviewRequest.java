package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class ReviewRequest {
    private Integer evidenceId;
    private String newJudgment;     // HIT or MISS
    private String reason;
    private Integer scoreAdjustment;
}
