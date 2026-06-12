package icu.secnotes.service;

import icu.secnotes.pojo.EvidenceRecord;
import icu.secnotes.pojo.ScoreDetail;
import icu.secnotes.pojo.dto.EvidenceSubmitRequest;
import java.util.List;
import java.util.Map;

public interface EvidenceService {

    EvidenceRecord submitEvidence(Integer userId, EvidenceSubmitRequest request);

    List<EvidenceRecord> getEvidenceByInstanceAndCheckpoint(Integer instanceId, Integer checkpointId);

    List<EvidenceRecord> getEvidenceByInstance(Integer instanceId);

    List<EvidenceRecord> getPendingReview(Integer taskId);

    ScoreDetail getScoreDetail(Integer instanceId, Integer checkpointId);

    List<ScoreDetail> getScoreDetails(Integer instanceId);

    Map<String, Object> getInstanceScoreSummary(Integer instanceId);
}
