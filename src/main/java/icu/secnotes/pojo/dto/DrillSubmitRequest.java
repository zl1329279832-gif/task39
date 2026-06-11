package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class DrillSubmitRequest {
    private Integer taskId;
    private Integer checkpointId;
    private String payloadSummary;
    private String evidence;
    private Integer elapsedSeconds;
    private Integer hintsUsed;
}
