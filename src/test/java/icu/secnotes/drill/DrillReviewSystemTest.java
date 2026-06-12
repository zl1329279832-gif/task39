package icu.secnotes.drill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.dto.*;
import icu.secnotes.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 复核系统测试
 * 验证复核工单创建、审批、改判、批量复核、自动创建、复核规则
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillReviewSystemTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;
    private String user2Token;

    // 基本任务（用于复核测试）
    private Integer basicTaskId;
    private Integer basicCp1Id;
    private Integer basicCp2Id;
    private Integer basicCp3Id;

    // evidenceReviewRequired 任务
    private Integer reviewRequiredTaskId;
    private Integer reviewRequiredCpId;

    // 复核规则任务
    private Integer reviewRuleTaskId;
    private Integer reviewRuleCpId;

    // 尝试记录ID
    private Integer guestAttemptCp1Id;
    private Integer guestAttemptCp2Id;
    private Integer guestAttemptCp3Id;

    // 复核单ID
    private Integer ticket1Id;
    private Integer ticket2Id;

    @BeforeAll
    void setup() {
        Map<String, Object> adminClaims = new HashMap<>();
        adminClaims.put("id", 1);
        adminClaims.put("username", "admin");
        adminClaims.put("name", "系统管理员");
        adminToken = JwtUtils.generateJwt(adminClaims);

        Map<String, Object> guestClaims = new HashMap<>();
        guestClaims.put("id", 3);
        guestClaims.put("username", "guest");
        guestClaims.put("name", "访客用户");
        guestToken = JwtUtils.generateJwt(guestClaims);

        Map<String, Object> user2Claims = new HashMap<>();
        user2Claims.put("id", 2);
        user2Claims.put("username", "zhangsan");
        user2Claims.put("name", "张三");
        user2Token = JwtUtils.generateJwt(user2Claims);

        try {
            // 1. 创建基本任务（3个检查点）
            DrillTaskCreateRequest basicReq = new DrillTaskCreateRequest();
            basicReq.setTitle("复核系统测试任务");
            basicReq.setDescription("用于验证复核工单流程");
            basicReq.setDifficulty("medium");

            List<DrillTaskCreateRequest.CheckpointDef> basicCps = new ArrayList<>();

            DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
            cp1.setVulnCategory("SQLI_NUMERIC");
            cp1.setCheckpointOrder(1);
            cp1.setMode("EXPLOIT");
            cp1.setMaxScore(100);
            cp1.setMaxHints(3);
            cp1.setTimeLimit(1800);
            basicCps.add(cp1);

            DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
            cp2.setVulnCategory("SQLI_STRING");
            cp2.setCheckpointOrder(2);
            cp2.setMode("EXPLOIT");
            cp2.setMaxScore(100);
            cp2.setMaxHints(3);
            cp2.setTimeLimit(1800);
            basicCps.add(cp2);

            DrillTaskCreateRequest.CheckpointDef cp3 = new DrillTaskCreateRequest.CheckpointDef();
            cp3.setVulnCategory("SQLI_UNION");
            cp3.setCheckpointOrder(3);
            cp3.setMode("EXPLOIT");
            cp3.setMaxScore(100);
            cp3.setMaxHints(3);
            cp3.setTimeLimit(1800);
            basicCps.add(cp3);

            basicReq.setCheckpoints(basicCps);

            MvcResult basicResult = mockMvc.perform(post("/drill/admin/tasks")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(basicReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode basicJson = objectMapper.readTree(basicResult.getResponse().getContentAsString());
            basicTaskId = basicJson.get("data").get("task").get("id").asInt();
            basicCp1Id = basicJson.get("data").get("checkpoints").get(0).get("id").asInt();
            basicCp2Id = basicJson.get("data").get("checkpoints").get(1).get("id").asInt();
            basicCp3Id = basicJson.get("data").get("checkpoints").get(2).get("id").asInt();

            // guest 通过 cp1 和 cp2（cp3 故意不通过）
            submitExploit(guestToken, basicTaskId, basicCp1Id,
                    "id=1 OR 1=1",
                    "[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
            submitExploit(guestToken, basicTaskId, basicCp2Id,
                    "username=admin' OR '1'='1",
                    "{\"username\":\"admin\",\"password\":\"secret123\",\"role\":\"admin\"}");
            // cp3 提交错误证据（不通过）
            submitExploit(guestToken, basicTaskId, basicCp3Id,
                    "wrong payload", "wrong evidence");

            // 获取尝试记录ID
            MvcResult attemptsResult = mockMvc.perform(get("/drill/student/attempts/" + basicTaskId)
                            .header("Authorization", guestToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode attemptsJson = objectMapper.readTree(attemptsResult.getResponse().getContentAsString());
            JsonNode attempts = attemptsJson.get("data");
            for (JsonNode a : attempts) {
                int cpId = a.get("checkpointId").asInt();
                int attemptId = a.get("id").asInt();
                if (cpId == basicCp1Id) guestAttemptCp1Id = attemptId;
                else if (cpId == basicCp2Id) guestAttemptCp2Id = attemptId;
                else if (cpId == basicCp3Id) guestAttemptCp3Id = attemptId;
            }

            // 2. 创建 evidenceReviewRequired 任务
            DrillTaskCreateRequest reviewReq = new DrillTaskCreateRequest();
            reviewReq.setTitle("需要复核的任务");
            reviewReq.setDescription("提交后自动创建复核单");
            reviewReq.setDifficulty("easy");
            reviewReq.setEvidenceReviewRequired(1);

            DrillTaskCreateRequest.CheckpointDef reviewCp = new DrillTaskCreateRequest.CheckpointDef();
            reviewCp.setVulnCategory("SQLI_NUMERIC");
            reviewCp.setCheckpointOrder(1);
            reviewCp.setMode("EXPLOIT");
            reviewCp.setMaxScore(100);
            reviewCp.setMaxHints(3);
            reviewCp.setTimeLimit(1800);
            reviewReq.setCheckpoints(Collections.singletonList(reviewCp));

            MvcResult reviewResult = mockMvc.perform(post("/drill/admin/tasks")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reviewReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode reviewJson = objectMapper.readTree(reviewResult.getResponse().getContentAsString());
            reviewRequiredTaskId = reviewJson.get("data").get("task").get("id").asInt();
            reviewRequiredCpId = reviewJson.get("data").get("checkpoints").get(0).get("id").asInt();

            // 3. 创建复核规则任务
            DrillTaskCreateRequest ruleReq = new DrillTaskCreateRequest();
            ruleReq.setTitle("复核规则测试任务");
            ruleReq.setDescription("用于测试自动通过和人工复核");
            ruleReq.setDifficulty("medium");

            DrillTaskCreateRequest.CheckpointDef ruleCp = new DrillTaskCreateRequest.CheckpointDef();
            ruleCp.setVulnCategory("SQLI_NUMERIC");
            ruleCp.setCheckpointOrder(1);
            ruleCp.setMode("EXPLOIT");
            ruleCp.setMaxScore(100);
            ruleCp.setMaxHints(1);
            ruleCp.setTimeLimit(1800);
            ruleReq.setCheckpoints(Collections.singletonList(ruleCp));

            MvcResult ruleResult = mockMvc.perform(post("/drill/admin/tasks")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ruleReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode ruleJson = objectMapper.readTree(ruleResult.getResponse().getContentAsString());
            reviewRuleTaskId = ruleJson.get("data").get("task").get("id").asInt();
            reviewRuleCpId = ruleJson.get("data").get("checkpoints").get(0).get("id").asInt();

        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }
    }

    @Test
    @Order(1)
    void testCreateReviewTicket_Success() throws Exception {
        ReviewTicketCreateRequest req = new ReviewTicketCreateRequest();
        req.setAttemptId(guestAttemptCp1Id);
        req.setReason("需要确认提交证据的有效性");

        MvcResult result = mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andExpect(jsonPath("$.data.originalPassed").value(1))
                .andExpect(jsonPath("$.data.originalScore").value(100))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        ticket1Id = json.get("data").get("id").asInt();
    }

    @Test
    @Order(2)
    void testApproveReviewTicket() throws Exception {
        ReviewDecisionRequest req = new ReviewDecisionRequest();
        req.setTicketId(ticket1Id);
        req.setDecision("approved");
        req.setReviewComment("证据有效，确认通过");

        mockMvc.perform(post("/drill/admin/review/decide")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.reviewComment").value("证据有效，确认通过"));
    }

    @Test
    @Order(3)
    void testOverrideReviewTicket_UpdatesScore() throws Exception {
        // 先为 cp3（未通过）创建复核单
        ReviewTicketCreateRequest createReq = new ReviewTicketCreateRequest();
        createReq.setAttemptId(guestAttemptCp3Id);
        createReq.setReason("学生申诉，要求改判");

        MvcResult createResult = mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int overrideTicketId = createJson.get("data").get("id").asInt();

        // 提交改判决策
        ReviewDecisionRequest decideReq = new ReviewDecisionRequest();
        decideReq.setTicketId(overrideTicketId);
        decideReq.setDecision("overridden");
        decideReq.setOverriddenPassed(1);
        decideReq.setOverriddenScore(90);
        decideReq.setReviewComment("经复核，证据基本有效，给予90分");

        mockMvc.perform(post("/drill/admin/review/decide")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("overridden"))
                .andExpect(jsonPath("$.data.overriddenScore").value(90));

        // 验证尝试记录已更新
        MvcResult attemptResult = mockMvc.perform(get("/drill/student/attempts/" + basicTaskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode attempts = objectMapper.readTree(attemptResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode a : attempts) {
            if (a.get("checkpointId").asInt() == basicCp3Id) {
                found = true;
                Assertions.assertEquals(1, a.get("passed").asInt(), "改判后应标记为通过");
                Assertions.assertEquals(90, a.get("score").asInt(), "改判后分数应为90");
            }
        }
        Assertions.assertTrue(found, "应找到 cp3 的尝试记录");
    }

    @Test
    @Order(4)
    void testRejectReviewTicket() throws Exception {
        // 为 cp2 创建复核单并拒绝
        ReviewTicketCreateRequest createReq = new ReviewTicketCreateRequest();
        createReq.setAttemptId(guestAttemptCp2Id);
        createReq.setReason("存疑提交");

        MvcResult createResult = mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int rejectTicketId = createJson.get("data").get("id").asInt();

        ReviewDecisionRequest decideReq = new ReviewDecisionRequest();
        decideReq.setTicketId(rejectTicketId);
        decideReq.setDecision("rejected");
        decideReq.setReviewComment("证据不充分，拒绝通过");

        mockMvc.perform(post("/drill/admin/review/decide")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("rejected"));
    }

    @Test
    @Order(5)
    void testDuplicatePendingTicket_Rejected() throws Exception {
        // guestAttemptCp1Id 的复核单已被处理（approved），可以再创建
        // 但先创建一个新复核单
        ReviewTicketCreateRequest req1 = new ReviewTicketCreateRequest();
        req1.setAttemptId(guestAttemptCp1Id);
        req1.setReason("二次复核");

        mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 再次创建相同attemptId的复核单 → 应该失败
        ReviewTicketCreateRequest req2 = new ReviewTicketCreateRequest();
        req2.setAttemptId(guestAttemptCp1Id);
        req2.setReason("重复创建");

        mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @Order(6)
    void testGuestCannotCreateReviewTicket() throws Exception {
        ReviewTicketCreateRequest req = new ReviewTicketCreateRequest();
        req.setAttemptId(guestAttemptCp1Id);
        req.setReason("学员自行申请复核");

        mockMvc.perform(post("/drill/admin/review/tickets")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void testBatchReview_ApproveAll() throws Exception {
        // 创建另一个任务用于批量复核
        DrillTaskCreateRequest batchReq = new DrillTaskCreateRequest();
        batchReq.setTitle("批量复核测试任务");
        batchReq.setDifficulty("easy");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
            cp.setVulnCategory("SQLI_NUMERIC");
            cp.setCheckpointOrder(i + 1);
            cp.setMode("EXPLOIT");
            cp.setMaxScore(100);
            cp.setMaxHints(3);
            cp.setTimeLimit(1800);
            cps.add(cp);
        }
        batchReq.setCheckpoints(cps);

        MvcResult createResult = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int batchTaskId = createJson.get("data").get("task").get("id").asInt();
        int batchCp1 = createJson.get("data").get("checkpoints").get(0).get("id").asInt();
        int batchCp2 = createJson.get("data").get("checkpoints").get(1).get("id").asInt();

        // user2(zhangsan) 提交两个检查点
        submitExploit(user2Token, batchTaskId, batchCp1,
                "id=1 OR 1=1",
                "[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
        submitExploit(user2Token, batchTaskId, batchCp2,
                "id=1 OR 1=1",
                "[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");

        // 获取尝试记录ID
        MvcResult attemptsResult = mockMvc.perform(get("/drill/student/attempts/" + batchTaskId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode attempts = objectMapper.readTree(attemptsResult.getResponse().getContentAsString()).get("data");
        List<Integer> attemptIds = new ArrayList<>();
        for (JsonNode a : attempts) {
            attemptIds.add(a.get("id").asInt());
        }

        // 批量复核
        BatchReviewRequest batchReview = new BatchReviewRequest();
        batchReview.setTaskId(batchTaskId);
        batchReview.setDecision("approved");
        batchReview.setAttemptIds(attemptIds);
        batchReview.setReviewComment("批量通过");

        mockMvc.perform(post("/drill/admin/review/batch")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(8)
    void testEvidenceReviewRequired_AutoCreatesTicket() throws Exception {
        // user2 提交到 evidenceReviewRequired 任务
        submitExploit(user2Token, reviewRequiredTaskId, reviewRequiredCpId,
                "id=1 OR 1=1",
                "[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");

        // 验证自动创建了复核单
        MvcResult ticketsResult = mockMvc.perform(get("/drill/admin/tasks/" + reviewRequiredTaskId + "/review-tickets")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("pending"))
                .andReturn();
    }

    @Test
    @Order(9)
    void testReviewRule_SaveAndGet() throws Exception {
        // 保存复核规则
        ReviewRuleRequest ruleReq = new ReviewRuleRequest();
        ruleReq.setTaskId(reviewRuleTaskId);
        ruleReq.setAutoApproveThreshold(80);
        ruleReq.setManualReviewBelow(40);
        ruleReq.setManualReviewTriggers("[\"低分提交\",\"异常证据\"]");

        mockMvc.perform(post("/drill/admin/review/rules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(reviewRuleTaskId))
                .andExpect(jsonPath("$.data.autoApproveThreshold").value(80))
                .andExpect(jsonPath("$.data.manualReviewBelow").value(40));

        // 获取复核规则
        mockMvc.perform(get("/drill/admin/review/rules/" + reviewRuleTaskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(reviewRuleTaskId));
    }

    @Test
    @Order(10)
    void testReviewRule_ManualReviewTriggered() throws Exception {
        // 设置规则：autoApprove=80, manualReviewBelow=95（高阈值，让正常提交触发人工复核）
        ReviewRuleRequest ruleReq = new ReviewRuleRequest();
        ruleReq.setTaskId(reviewRuleTaskId);
        ruleReq.setAutoApproveThreshold(100);
        ruleReq.setManualReviewBelow(95);
        ruleReq.setManualReviewTriggers("[\"分数低于95需人工复核\"]");

        mockMvc.perform(post("/drill/admin/review/rules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleReq)))
                .andExpect(status().isOk());

        // user2 正常提交（分数100 >= manualReviewBelow=95，但 < autoApproveThreshold=100）
        // 因为分数 100 不小于 manualReviewBelow(95), 不会触发人工复核
        // 我们需要让分数 < 95 才触发，使用 hintsUsed=5 来扣分
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(reviewRuleTaskId);
        req.setCheckpointId(reviewRuleCpId);
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
        req.setElapsedSeconds(120);
        req.setHintsUsed(5);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1));

        // 验证触发了人工复核（分数 100 - (5-1)*5 = 80 < 95）
        MvcResult ticketsResult = mockMvc.perform(get("/drill/admin/tasks/" + reviewRuleTaskId + "/review-tickets")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode tickets = objectMapper.readTree(ticketsResult.getResponse().getContentAsString()).get("data");
        Assertions.assertTrue(tickets.size() >= 1, "应至少有一个待复核工单");
        Assertions.assertEquals("pending", tickets.get(0).get("reviewStatus").asText());
    }

    @Test
    @Order(11)
    void testGetTaskReviewTickets() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/" + basicTaskId + "/review-tickets")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNotEmpty());
    }

    @Test
    @Order(12)
    void testOverrideRecalculatesScoreSummary() throws Exception {
        // 验证基本任务的分数统计在改判后已更新
        MvcResult scoreResult = mockMvc.perform(get("/drill/student/scores/" + basicTaskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode scoreData = objectMapper.readTree(scoreResult.getResponse().getContentAsString()).get("data");
        int totalScore = scoreData.get("totalScore").asInt();
        int checkpointsPassed = scoreData.get("checkpointsPassed").asInt();
        // cp1(100) + cp2(100) + cp3(90 after override) = 290
        Assertions.assertEquals(290, totalScore, "总分应为 290 (100+100+90)");
        Assertions.assertEquals(3, checkpointsPassed, "3个检查点都应通过");
    }

    @Test
    @Order(13)
    void testListPendingTickets() throws Exception {
        mockMvc.perform(get("/drill/admin/review/tickets")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(14)
    void testGetReviewTicket() throws Exception {
        mockMvc.perform(get("/drill/admin/review/tickets/" + ticket1Id)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(ticket1Id));
    }

    @Test
    @Order(15)
    void testTaskRuleFields_CreateWithNewFields() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("任务规则测试");
        req.setDifficulty("hard");
        req.setMaxHintCount(2);
        req.setTimeLimitMinutes(10);
        req.setAllowRetry(0);
        req.setEvidenceReviewRequired(0);
        req.setPrerequisiteKnowledge("[\"SQL基础\",\"HTTP协议\"]");

        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_NUMERIC");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(5);
        cp.setTimeLimit(3600);
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.maxHintCount").value(2))
                .andExpect(jsonPath("$.data.task.timeLimitMinutes").value(10))
                .andExpect(jsonPath("$.data.task.allowRetry").value(0))
                .andReturn();
    }

    // ===== 辅助方法 =====

    private void submitExploit(String token, int taskId, int checkpointId,
                               String payload, String evidence) throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(checkpointId);
        req.setPayloadSummary(payload);
        req.setEvidence(evidence);
        req.setElapsedSeconds(120);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
