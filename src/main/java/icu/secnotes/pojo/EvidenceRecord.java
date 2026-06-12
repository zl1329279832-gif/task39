package icu.secnotes.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceRecord {
    private Integer id;
    private Integer instanceId;
    private Integer taskId;
    private Integer checkpointId;
    private Integer userId;
    private String evidenceType;    // PAYLOAD, SCREENSHOT_HASH, REQUEST_LOG
    private String content;
    private String mode;            // EXPLOIT, DEFENSE
    private String autoJudgment;    // HIT, MISS, PENDING
    private String adminJudgment;   // HIT, MISS, or null
    private Integer reviewId;
    private String submissionHash;  // SHA-256 dedup key
    private Integer elapsedSeconds;
    private Integer hintsUsed;
    private LocalDateTime createTime;
}
