package icu.secnotes.drill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
import icu.secnotes.pojo.dto.EvidenceSubmitRequest;
import icu.secnotes.pojo.dto.ReviewRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 分层演练任务与证据复核 集成测试
 * 覆盖:
 *  1. 任务创建与实例启动
 *  2. 靶点完成判定（自动判定 HIT/MISS）
 *  3. 证据复核（管理员改判 + 评分重算）
 *  4. 重复提交幂等（同一哈希不重复加分）
 *  5. safe/vulnerable (EXPLOIT/DEFENSE) 模式隔离
 *  6. 成绩统计与评分明细
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillEvidenceReviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;
    private String user2Token;

    // shared state across ordered tests
    private Integer taskId;
    private Integer exploitCheckpointId;
    private Integer defenseCheckpointId;
    private Integer instanceId;
    private Integer firstEvidenceId;
    private Integer defenseEvidenceId;

    @BeforeAll
    void init() {
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
        user2Claims.put("name", "审计员");
        user2Token = JwtUtils.generateJwt(user2Claims);
    }

    // ==================== 1. 任务创建与实例启动 ====================

    @Test
    @Order(1)
    void testCreateTask_WithExploitAndDefense() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("证据复核测试任务");
        req.setDescription("包含 EXPLOIT 和 DEFENSE 检查点，用于证据复核测试");
        req.setDifficulty("medium");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
        cp1.setVulnCategory("SQLI_NUMERIC");
        cp1.setCheckpointOrder(1);
        cp1.setMode("EXPLOIT");
        cp1.setMaxScore(100);
        cp1.setMaxHints(2);
        cp1.setTimeLimit(3600);
        cps.add(cp1);

        DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
        cp2.setVulnCategory("SQLI_NUMERIC");
        cp2.setCheckpointOrder(2);
        cp2.setMode("DEFENSE");
        cp2.setMaxScore(80);
        cp2.setMaxHints(3);
        cp2.setTimeLimit(3600);
        cps.add(cp2);

        req.setCheckpoints(cps);

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        taskId = data.get("task").get("id").asInt();
        assertNotNull(taskId);

        JsonNode checkpoints = data.get("checkpoints");
        assertEquals(2, checkpoints.size());
        exploitCheckpointId = checkpoints.get(0).get("id").asInt();
        defenseCheckpointId = checkpoints.get(1).get("id").asInt();

        assertEquals("EXPLOIT", checkpoints.get(0).get("mode").asText());
        assertEquals("DEFENSE", checkpoints.get(1).get("mode").asText());
    }

    @Test
    @Order(2)
    void testStartInstance_Success() throws Exception {
        MvcResult result = mockMvc.perform(post("/drill/student/instance/start/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.userId").value(3))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        instanceId = data.get("id").asInt();
        assertNotNull(instanceId);
        assertNotNull(data.get("rulesSnapshot").asText());
    }

    @Test
    @Order(3)
    void testStartInstance_Idempotent() throws Exception {
        // 再次启动同一任务，应返回已有实例
        MvcResult result = mockMvc.perform(post("/drill/student/instance/start/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(instanceId, data.get("id").asInt());
    }

    @Test
    @Order(4)
    void testStartInstance_InvalidTask() throws Exception {
        mockMvc.perform(post("/drill/student/instance/start/99999")
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 2. 靶点完成判定 ====================

    @Test
    @Order(10)
    void testSubmitEvidence_ExploitHit() throws Exception {
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        // SQLI_NUMERIC verify pattern is "username" — evidence must contain "username"
        req.setContent("{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"}");
        req.setElapsedSeconds(300);
        req.setHintsUsed(1);

        MvcResult result = mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.autoJudgment").value("HIT"))
                .andExpect(jsonPath("$.data.mode").value("EXPLOIT"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        firstEvidenceId = data.get("id").asInt();
        assertNotNull(firstEvidenceId);
    }

    @Test
    @Order(11)
    void testSubmitEvidence_ExploitMiss() throws Exception {
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        req.setContent("normal_input_no_sqli");
        req.setElapsedSeconds(400);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.autoJudgment").value("MISS"));
    }

    @Test
    @Order(12)
    void testSubmitEvidence_ScreenshotHash() throws Exception {
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("SCREENSHOT_HASH");
        req.setContent("screenshot hash: already passed, has \"username\" in response");
        req.setElapsedSeconds(500);
        req.setHintsUsed(0);

        // already passed, evidence recorded but no double scoring
        mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 3. 重复提交幂等 ====================

    @Test
    @Order(20)
    void testDuplicateSubmission_Idempotent() throws Exception {
        // 提交与 Order(10) 完全相同的内容
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        // same content as Order(10) — must produce same hash
        req.setContent("{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"}");
        req.setElapsedSeconds(300);
        req.setHintsUsed(1);

        MvcResult result = mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        // 应返回同一条证据记录（幂等）
        assertEquals(firstEvidenceId, data.get("id").asInt());
    }

    @Test
    @Order(21)
    void testNoDoubleScoring_AfterCheckpointPassed() throws Exception {
        // 获取当前评分明细，确认只有一次得分
        MvcResult result = mockMvc.perform(get("/drill/student/instance/" + instanceId + "/scores")
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        int totalScore = data.get("totalScore").asInt();
        // EXPLOIT checkpoint maxScore=100, hints=1 (within max 2), elapsed=300 (within 3600)
        // No deductions expected: score should be 100
        assertEquals(100, totalScore);
    }

    // ==================== 4. safe/vulnerable (EXPLOIT/DEFENSE) 模式隔离 ====================

    @Test
    @Order(30)
    void testDefenseMode_Submit() throws Exception {
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(defenseCheckpointId);
        req.setEvidenceType("PAYLOAD");
        req.setContent("1 OR 1=1");  // attack payload for defense mode
        req.setElapsedSeconds(200);
        req.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mode").value("DEFENSE"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        defenseEvidenceId = data.get("id").asInt();
        assertNotNull(defenseEvidenceId);
    }

    @Test
    @Order(31)
    void testModeIsolation_EvidenceSeparation() throws Exception {
        // 获取实例的全部证据
        MvcResult result = mockMvc.perform(get("/drill/student/evidence/" + instanceId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.isArray());

        // 验证 EXPLOIT 和 DEFENSE 证据独立存储
        int exploitCount = 0, defenseCount = 0;
        for (JsonNode ev : data) {
            String mode = ev.get("mode").asText();
            if ("EXPLOIT".equals(mode)) exploitCount++;
            else if ("DEFENSE".equals(mode)) defenseCount++;
        }
        assertTrue(exploitCount >= 2, "应有至少2条 EXPLOIT 证据");
        assertTrue(defenseCount >= 1, "应有至少1条 DEFENSE 证据");
    }

    @Test
    @Order(32)
    void testModeIsolation_ScoresSeparate() throws Exception {
        // 评分明细应按检查点独立记录
        MvcResult result = mockMvc.perform(get("/drill/student/instance/" + instanceId + "/scores")
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        JsonNode scoreDetails = data.get("scoreDetails");

        // EXPLOIT checkpoint passed with 100 points
        // DEFENSE checkpoint may or may not have passed (depends on RestTemplate availability in test)
        assertNotNull(scoreDetails);
        assertTrue(scoreDetails.isArray());
        // At minimum the EXPLOIT checkpoint should have a score detail
        boolean foundExploitScore = false;
        for (JsonNode sd : scoreDetails) {
            if (sd.get("checkpointId").asInt() == exploitCheckpointId) {
                foundExploitScore = true;
                assertEquals(1, sd.get("passed").asInt());
                assertEquals(100, sd.get("baseScore").asInt());
            }
        }
        assertTrue(foundExploitScore, "EXPLOIT 检查点应有评分记录");
    }

    // ==================== 5. 证据复核 ====================

    @Test
    @Order(40)
    void testAdminReview_PendingList() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/review/pending/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertNotNull(data);
        assertTrue(data.isArray());
    }

    @Test
    @Order(41)
    void testAdminReview_OverrideJudgment() throws Exception {
        // 管理员将 EXPLOIT HIT 改判为 MISS
        ReviewRequest req = new ReviewRequest();
        req.setEvidenceId(firstEvidenceId);
        req.setNewJudgment("MISS");
        req.setReason("证据不充分，需要补充完整的注入链");
        req.setScoreAdjustment(0);

        MvcResult result = mockMvc.perform(post("/drill/admin/review")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.originalJudgment").value("HIT"))
                .andExpect(jsonPath("$.data.newJudgment").value("MISS"))
                .andReturn();

        // 验证分数被撤销
        MvcResult scoreResult = mockMvc.perform(
                        get("/drill/admin/instance/" + instanceId + "/scores")
                                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode scoreData = objectMapper.readTree(scoreResult.getResponse().getContentAsString()).get("data");
        // EXPLOIT checkpoint score should be revoked (0 or adjusted)
        JsonNode details = scoreData.get("scoreDetails");
        for (JsonNode sd : details) {
            if (sd.get("checkpointId").asInt() == exploitCheckpointId) {
                assertEquals(0, sd.get("passed").asInt(), "改判MISS后应标记为未通过");
            }
        }
    }

    @Test
    @Order(42)
    void testAdminReview_RestoreJudgment() throws Exception {
        // 管理员重新改判为 HIT（并加分调整）
        ReviewRequest req = new ReviewRequest();
        req.setEvidenceId(firstEvidenceId);
        req.setNewJudgment("HIT");
        req.setReason("重新审查后确认注入成功");
        req.setScoreAdjustment(-10);  // 扣10分作为迟交惩罚

        mockMvc.perform(post("/drill/admin/review")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.originalJudgment").value("MISS"))
                .andExpect(jsonPath("$.data.newJudgment").value("HIT"))
                .andExpect(jsonPath("$.data.scoreAdjustment").value(-10));
    }

    @Test
    @Order(43)
    void testAdminReview_History() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/review/history/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.isArray());
        assertTrue(data.size() >= 2, "应有至少2条复核记录");
    }

    @Test
    @Order(44)
    void testAdminReview_InvalidJudgment() throws Exception {
        ReviewRequest req = new ReviewRequest();
        req.setEvidenceId(firstEvidenceId);
        req.setNewJudgment("INVALID");
        req.setReason("test");

        mockMvc.perform(post("/drill/admin/review")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 6. 权限绕过拦截 ====================

    @Test
    @Order(50)
    void testPermission_GuestCannotAccessAdminReview() throws Exception {
        // guest 不能访问管理员复核端点 — 拦截器返回 HTTP 403
        mockMvc.perform(get("/drill/admin/review/pending/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(51)
    void testPermission_CannotAccessOtherUserInstance() throws Exception {
        // user2 不能查看 guest 的证据
        mockMvc.perform(get("/drill/student/evidence/" + instanceId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("无权查看此实例"));
    }

    @Test
    @Order(52)
    void testPermission_CannotSubmitToOtherUserInstance() throws Exception {
        // user2 不能向 guest 的实例提交证据
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(instanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        req.setContent("attack");
        req.setElapsedSeconds(100);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @Order(53)
    void testPermission_UnauthenticatedRejected() throws Exception {
        // 无 token 时拦截器返回 HTTP 401
        mockMvc.perform(post("/drill/student/evidence/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 7. 成绩统计 ====================

    @Test
    @Order(60)
    void testScoreStatistics_InstanceSummary() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/drill/student/instance/" + instanceId + "/scores")
                                .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.instanceId").value(instanceId))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.userId").value(3))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        int totalScore = data.get("totalScore").asInt();
        int maxPossible = data.get("maxPossible").asInt();
        assertEquals(180, maxPossible, "总满分应为 100 + 80 = 180");
        assertTrue(totalScore >= 0, "总分应非负");
    }

    @Test
    @Order(61)
    void testScoreStatistics_AdminViewInstanceScores() throws Exception {
        mockMvc.perform(get("/drill/admin/instance/" + instanceId + "/scores")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.instanceId").value(instanceId))
                .andExpect(jsonPath("$.data.scoreDetails").isArray());
    }

    @Test
    @Order(62)
    void testScoreStatistics_AdminViewTaskInstances() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/instances/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.isArray());
        assertTrue(data.size() >= 1, "应有至少1个任务实例");
    }

    @Test
    @Order(63)
    void testScoreStatistics_StudentMyInstances() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/student/instances")
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.isArray());
        assertTrue(data.size() >= 1);
    }

    // ==================== 8. 审计日志 ====================

    @Test
    @Order(70)
    void testAuditLog_RecentLogs() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/audit/recent")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.isArray());
        assertTrue(data.size() >= 1, "应有至少1条审计日志");
    }

    // ==================== 9. 提示扣分与超时扣分 ====================

    @Test
    @Order(80)
    void testHintDeduction() throws Exception {
        // user2 创建自己的实例
        mockMvc.perform(post("/drill/student/instance/start/" + taskId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult instResult = mockMvc.perform(get("/drill/student/instance/" + taskId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode instData = objectMapper.readTree(instResult.getResponse().getContentAsString()).get("data");
        int user2InstanceId = instData.get("id").asInt();

        // 提交证据时 hintsUsed=5 超过 maxHints=2，应产生扣分
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(user2InstanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        // evidence must match "username" pattern to HIT
        req.setContent("Extracted: {\"username\":\"admin\",\"password\":\"secret\"}");
        req.setElapsedSeconds(100);
        req.setHintsUsed(5);  // maxHints=2, excess=3, deduction=15

        mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.autoJudgment").value("HIT"));

        // 检查评分明细
        MvcResult scoreResult = mockMvc.perform(
                        get("/drill/student/instance/" + user2InstanceId + "/scores")
                                .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode scoreData = objectMapper.readTree(scoreResult.getResponse().getContentAsString()).get("data");
        JsonNode details = scoreData.get("scoreDetails");
        for (JsonNode sd : details) {
            if (sd.get("checkpointId").asInt() == exploitCheckpointId) {
                assertEquals(15, sd.get("hintDeduction").asInt(), "提示扣分应为 (5-2)*5 = 15");
                assertEquals(85, sd.get("finalScore").asInt(), "最终得分应为 100-15 = 85");
            }
        }
    }

    @Test
    @Order(81)
    void testTimeDeduction() throws Exception {
        // admin 创建自己的实例
        mockMvc.perform(post("/drill/student/instance/start/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult instResult = mockMvc.perform(get("/drill/student/instance/" + taskId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode instData = objectMapper.readTree(instResult.getResponse().getContentAsString()).get("data");
        int adminInstanceId = instData.get("id").asInt();

        // 提交证据时 elapsedSeconds=4200 超过 timeLimit=3600，应产生超时扣分
        EvidenceSubmitRequest req = new EvidenceSubmitRequest();
        req.setInstanceId(adminInstanceId);
        req.setCheckpointId(exploitCheckpointId);
        req.setEvidenceType("PAYLOAD");
        // evidence must match "username" pattern to HIT
        req.setContent("Data leak: \"username\" found via time-based injection");
        req.setElapsedSeconds(4200);  // timeout 600s, deduction = min(20, 600/60*5) = 20
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/evidence/submit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 检查评分明细
        MvcResult scoreResult = mockMvc.perform(
                        get("/drill/student/instance/" + adminInstanceId + "/scores")
                                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode scoreData = objectMapper.readTree(scoreResult.getResponse().getContentAsString()).get("data");
        JsonNode details = scoreData.get("scoreDetails");
        for (JsonNode sd : details) {
            if (sd.get("checkpointId").asInt() == exploitCheckpointId) {
                assertEquals(20, sd.get("timeDeduction").asInt(), "超时扣分应封顶为20");
                assertEquals(80, sd.get("finalScore").asInt(), "最终得分应为 100-20 = 80");
            }
        }
    }

    // ==================== 10. 规则快照 ====================

    @Test
    @Order(90)
    void testRulesSnapshot_PreservedOnInstanceStart() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/student/instance/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String snapshot = data.get("rulesSnapshot").asText();
        assertNotNull(snapshot);
        assertTrue(snapshot.contains("checkpoints"), "快照应包含检查点配置");
        assertTrue(snapshot.contains("SQLI_NUMERIC"), "快照应包含漏洞类别");
    }
}
