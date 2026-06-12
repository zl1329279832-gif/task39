package icu.secnotes.pojo.dto;

import lombok.Data;

@Data
public class EvidenceSubmitRequest {
    private Integer instanceId;
    private Integer taskId;
    private Integer checkpointId;
    private String evidenceType;    // PAYLOAD, SCREENSHOT_HASH, REQUEST_LOG
    private String content;
    private Integer elapsedSeconds;
    private Integer hintsUsed;
}
