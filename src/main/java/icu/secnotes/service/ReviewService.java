package icu.secnotes.service;

import icu.secnotes.pojo.ReviewRecord;
import icu.secnotes.pojo.dto.ReviewRequest;
import java.util.List;

public interface ReviewService {

    ReviewRecord review(Integer reviewerId, ReviewRequest request);

    List<ReviewRecord> getReviewsByEvidence(Integer evidenceId);

    List<ReviewRecord> getReviewsByInstance(Integer instanceId);

    List<ReviewRecord> getReviewsByTask(Integer taskId);
}
