package icu.secnotes.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrillSubmitRequest {
    private Integer taskId;
    private Integer checkpointId;
    private String payloadSummary;
    private String evidence;
    private Integer elapsedSeconds;
    private Integer hintsUsed;
}
