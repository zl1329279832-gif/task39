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
    private String mode; // EXPLOIT 或 DEFENSE；缺省时回退到检查点声明的 mode
}
