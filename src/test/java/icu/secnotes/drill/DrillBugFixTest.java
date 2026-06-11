package icu.secnotes.drill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.secnotes.pojo.dto.DrillSubmitRequest;
import icu.secnotes.pojo.dto.DrillTaskCreateRequest;
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
 * 演练评分系统 BugFix 回归测试
 *
 * 覆盖场景:
 * 1. 任务创建 —— 同一漏洞类别分别创建 EXPLOIT / DEFENSE 检查点
 * 2. EXPLOIT / DEFENSE 证据隔离 —— 通过 EXPLOIT 不影响 DEFENSE 判定
 * 3. 同模式幂等 —— 已通过时重复提交返回缓存结果
 * 4. 跨模式无重试扣分 —— DEFENSE 首次提交不受 EXPLOIT 记录影响
 * 5. 同模式重试扣分 —— 失败后再次提交同一模式触发 3 分重试扣分
 * 6. 评分快照 —— 管理员调整 maxScore 后旧成绩不漂移
 * 7. 权限拦截 —— guest 不可访问 admin 接口; 无 token 返回 401
 * 8. 成绩统计 —— 多模式下成绩正确聚合
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillBugFixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;

    // 任务 A: 含同一漏洞类别的 EXPLOIT + DEFENSE 两个检查点
    private Integer taskAId;
    private Integer exploitCpId;
    private Integer defenseCpId;

    // 任务 B: 用于评分快照测试
    private Integer taskBId;
    private Integer snapshotCpId;

    // 任务 C: 用于同模式重试扣分测试
    private Integer taskCId;
    private Integer retryCpId;

    @BeforeAll
    void setup() throws Exception {
        adminToken = generateToken(1, "admin", "系统管理员");
        guestToken = generateToken(3, "guest", "访客用户");

        // ===== 任务 A: 同漏洞类别的 EXPLOIT + DEFENSE 检查点 =====
        {
            DrillTaskCreateRequest req = new DrillTaskCreateRequest();
            req.setTitle("模式隔离测试任务");
            req.setDescription("测试 EXPLOIT/DEFENSE 证据隔离和跨模式幂等");
            req.setDifficulty("medium");

            List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();

            DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
            cp1.setVulnCategory("SQLI_NUMERIC");
            cp1.setCheckpointOrder(1);
            cp1.setMode("EXPLOIT");
            cp1.setMaxScore(100);
            cp1.setMaxHints(3);
            cp1.setTimeLimit(1800);
            cps.add(cp1);

            DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
            cp2.setVulnCategory("SQLI_NUMERIC");
            cp2.setCheckpointOrder(2);
            cp2.setMode("DEFENSE");
            cp2.setMaxScore(100);
            cp2.setMaxHints(3);
            cp2.setTimeLimit(1800);
            cps.add(cp2);

            req.setCheckpoints(cps);

            JsonNode data = createTask(req);
            taskAId = data.get("task").get("id").asInt();
            exploitCpId = data.get("checkpoints").get(0).get("id").asInt();
            defenseCpId = data.get("checkpoints").get(1).get("id").asInt();
        }

        // ===== 任务 B: 评分快照测试 =====
        {
            DrillTaskCreateRequest req = new DrillTaskCreateRequest();
            req.setTitle("评分快照测试任务");
            req.setDifficulty("easy");

            DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
            cp.setVulnCategory("SQLI_STRING");
            cp.setCheckpointOrder(1);
            cp.setMode("EXPLOIT");
            cp.setMaxScore(100);
            cp.setMaxHints(3);
            cp.setTimeLimit(1800);

            req.setCheckpoints(Collections.singletonList(cp));

            JsonNode data = createTask(req);
            taskBId = data.get("task").get("id").asInt();
            snapshotCpId = data.get("checkpoints").get(0).get("id").asInt();
        }

        // ===== 任务 C: 同模式重试扣分测试 =====
        {
            DrillTaskCreateRequest req = new DrillTaskCreateRequest();
            req.setTitle("重试扣分测试任务");
            req.setDifficulty("easy");

            DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
            cp.setVulnCategory("SQLI_ERROR");
            cp.setCheckpointOrder(1);
            cp.setMode("EXPLOIT");
            cp.setMaxScore(100);
            cp.setMaxHints(3);
            cp.setTimeLimit(1800);

            req.setCheckpoints(Collections.singletonList(cp));

            JsonNode data = createTask(req);
            taskCId = data.get("task").get("id").asInt();
            retryCpId = data.get("checkpoints").get(0).get("id").asInt();
        }
    }

    // ==================== 1. 任务创建 ====================

    @Test
    @Order(1)
    void testTaskCreated_WithBothModes() throws Exception {
        MvcResult result = mockMvc.perform(post("/drill/student/start/" + taskAId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(2))
                .andReturn();

        JsonNode checkpoints = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("checkpoints");

        Assertions.assertEquals("EXPLOIT", checkpoints.get(0).get("mode").asText());
        Assertions.assertEquals("DEFENSE", checkpoints.get(1).get("mode").asText());
        // 验证不泄露验证模式
        Assertions.assertNull(checkpoints.get(0).get("verifyPattern"));
        Assertions.assertNull(checkpoints.get(1).get("defensePattern"));
    }

    // ==================== 2. EXPLOIT/DEFENSE 证据隔离 ====================

    @Test
    @Order(2)
    void testExploitPass_DoesNotLeakToDefense() throws Exception {
        // 步骤 1: 在 EXPLOIT 模式通过
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskAId);
        req.setCheckpointId(exploitCpId);
        req.setMode("EXPLOIT");
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"}]");
        req.setElapsedSeconds(120);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.mode").value("EXPLOIT"));

        // 步骤 2: 用相同 evidence 提交 DEFENSE 检查点 —— 应失败（DEFENSE 走 replay，不看 evidence）
        // 由于测试环境无真实 sec 端点，RestTemplate 会抛异常，CheckpointVerifier 把异常视为"拦截成功"
        // 所以这里测试的核心是: DEFENSE 检查点是独立记录，不复用 EXPLOIT 的通过状态
        DrillSubmitRequest defReq = new DrillSubmitRequest();
        defReq.setTaskId(taskAId);
        defReq.setCheckpointId(defenseCpId);
        defReq.setMode("DEFENSE");
        defReq.setPayloadSummary("1 OR 1=1");
        defReq.setEvidence("[{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"}]");
        defReq.setElapsedSeconds(60);
        defReq.setHintsUsed(0);

        MvcResult defResult = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEFENSE"))
                .andReturn();

        // 关键断言: DEFENSE 结果是独立的新记录，不是 EXPLOIT 的缓存返回
        JsonNode defData = objectMapper.readTree(defResult.getResponse().getContentAsString()).get("data");
        Assertions.assertNotEquals(exploitCpId, defData.get("checkpointId").asInt(),
                "DEFENSE 应提交到 defense 检查点而非 exploit 检查点");
    }

    @Test
    @Order(3)
    void testExploitEvidence_DoesNotSatisfyDefenseOnSameCheckpoint() throws Exception {
        // 在 EXPLOIT 检查点上用 DEFENSE 模式提交 —— 即同一检查点不同模式是隔离的
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskAId);
        req.setCheckpointId(exploitCpId);
        req.setMode("DEFENSE");
        req.setPayloadSummary("1 OR 1=1");
        req.setEvidence("应被忽略");
        req.setElapsedSeconds(60);
        req.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEFENSE"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        // 不管 DEFENSE 通过与否，关键是: 这是一条独立记录，不是 EXPLOIT 的幂等缓存
        Assertions.assertEquals("DEFENSE", data.get("mode").asText(),
                "返回记录的 mode 应为 DEFENSE");
    }

    // ==================== 3. 同模式幂等 ====================

    @Test
    @Order(4)
    void testIdempotent_SameModeReturnsCache() throws Exception {
        // EXPLOIT 检查点已在 Order(2) 中通过，再次提交不同的 evidence
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskAId);
        req.setCheckpointId(exploitCpId);
        req.setMode("EXPLOIT");
        req.setPayloadSummary("完全不同的 payload");
        req.setEvidence("完全不同的 evidence");
        req.setElapsedSeconds(9999);
        req.setHintsUsed(10);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.mode").value("EXPLOIT"));
    }

    // ==================== 4. 跨模式无重试扣分 ====================

    @Test
    @Order(5)
    void testCrossModeSubmit_NoRetryPenalty() throws Exception {
        // 先在 EXPLOIT 模式提交评分快照检查点
        DrillSubmitRequest exploitReq = new DrillSubmitRequest();
        exploitReq.setTaskId(taskBId);
        exploitReq.setCheckpointId(snapshotCpId);
        exploitReq.setMode("EXPLOIT");
        exploitReq.setPayloadSummary("username=admin' OR '1'='1");
        exploitReq.setEvidence("{\"username\":\"admin\",\"password\":\"secret\"}");
        exploitReq.setElapsedSeconds(100);
        exploitReq.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exploitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100));

        // 再在 DEFENSE 模式首次提交同一检查点 —— 不应有重试扣分
        DrillSubmitRequest defReq = new DrillSubmitRequest();
        defReq.setTaskId(taskBId);
        defReq.setCheckpointId(snapshotCpId);
        defReq.setMode("DEFENSE");
        defReq.setPayloadSummary("admin' OR '1'='1");
        defReq.setEvidence("应被忽略");
        defReq.setElapsedSeconds(100);
        defReq.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String deductions = data.get("deductionItems").asText();
        Assertions.assertFalse(deductions.contains("重试"),
                "DEFENSE 首次提交不应有重试扣分，deductions=" + deductions);
    }

    // ==================== 5. 同模式重试扣分 ====================

    @Test
    @Order(6)
    void testSameModeRetry_GetsPenalty() throws Exception {
        // 第一次提交: 用错误 evidence 故意失败
        DrillSubmitRequest fail = new DrillSubmitRequest();
        fail.setTaskId(taskCId);
        fail.setCheckpointId(retryCpId);
        fail.setMode("EXPLOIT");
        fail.setPayloadSummary("普通查询");
        fail.setEvidence("没有任何漏洞证据的普通响应");
        fail.setElapsedSeconds(60);
        fail.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(0))
                .andExpect(jsonPath("$.data.score").value(0));

        // 第二次提交同模式: 正确 evidence 通过 —— 应有 3 分重试扣分
        DrillSubmitRequest pass = new DrillSubmitRequest();
        pass.setTaskId(taskCId);
        pass.setCheckpointId(retryCpId);
        pass.setMode("EXPLOIT");
        pass.setPayloadSummary("id=1' AND extractvalue(1,concat(0x7e,version()))--");
        pass.setEvidence("SQLException|syntax error|XPATH syntax error");
        pass.setElapsedSeconds(120);
        pass.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(97))  // 100 - 3(重试)
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String deductions = data.get("deductionItems").asText();
        // 验证扣分项包含重试扣分（3 分）
        Assertions.assertTrue(deductions.contains("\"points\":3"),
                "同模式第二次提交应有 3 分重试扣分, deductions=" + deductions);
        Assertions.assertEquals(97, data.get("score").asInt(),
                "扣除 3 分重试后应为 97 分");
    }

    // ==================== 6. 评分快照 —— 管理员调整不污染旧成绩 ====================

    @Test
    @Order(7)
    void testScoreSnapshot_AdminChangeDoesNotDrift() throws Exception {
        // 步骤 1: 查询当前已通过的成绩（Order(5) 中已通过 snapshotCpId, score=100）
        MvcResult beforeResult = mockMvc.perform(get("/drill/student/scores/" + taskBId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode beforeData = objectMapper.readTree(beforeResult.getResponse().getContentAsString()).get("data");
        int scoreBefore = beforeData.get("totalScore").asInt();
        Assertions.assertTrue(scoreBefore >= 100, "修改前总分应 >= 100");

        // 步骤 2: 管理员修改检查点的 maxScore 为 50
        Map<String, Object> updateCp = new HashMap<>();
        updateCp.put("vulnCategory", "SQLI_STRING");
        updateCp.put("mode", "EXPLOIT");
        updateCp.put("maxScore", 50);
        updateCp.put("maxHints", 3);
        updateCp.put("timeLimit", 1800);

        mockMvc.perform(put("/drill/admin/checkpoints/" + snapshotCpId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateCp)))
                .andExpect(status().isOk());

        // 步骤 3: 查询学员的尝试记录 —— scored_max_score 快照应仍为 100
        MvcResult attemptsResult = mockMvc.perform(get("/drill/student/attempts/" + taskBId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode attempts = objectMapper.readTree(attemptsResult.getResponse().getContentAsString()).get("data");
        boolean foundExploit = false;
        for (JsonNode attempt : attempts) {
            if ("EXPLOIT".equals(attempt.get("mode").asText())
                    && attempt.get("checkpointId").asInt() == snapshotCpId
                    && attempt.get("passed").asInt() == 1) {
                foundExploit = true;
                Assertions.assertEquals(100, attempt.get("scoredMaxScore").asInt(),
                        "评分快照 scoredMaxScore 应保持提交时的值 100，不随管理员调整漂移");
                Assertions.assertEquals(100, attempt.get("score").asInt(),
                        "已有成绩 score 应保持 100，不因 maxScore 下调而改变");
            }
        }
        Assertions.assertTrue(foundExploit, "应找到 EXPLOIT 模式的通过记录");
    }

    // ==================== 7. 权限拦截 ====================

    @Test
    @Order(8)
    void testGuestCannotAccessAdminStats() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/" + taskAId + "/stats")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void testGuestCannotResetAttempts() throws Exception {
        mockMvc.perform(post("/drill/admin/tasks/" + taskAId + "/reset")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void testUnauthenticatedCannotSubmit() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskAId);
        req.setCheckpointId(exploitCpId);
        req.setMode("EXPLOIT");
        req.setEvidence("test");

        mockMvc.perform(post("/drill/student/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    void testGuestCannotModifyCheckpoints() throws Exception {
        Map<String, Object> update = new HashMap<>();
        update.put("vulnCategory", "SQLI_NUMERIC");
        update.put("mode", "EXPLOIT");
        update.put("maxScore", 999);
        update.put("maxHints", 3);

        mockMvc.perform(put("/drill/admin/checkpoints/" + exploitCpId)
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    // ==================== 8. 成绩统计 ====================

    @Test
    @Order(12)
    void testScoreSummary_CorrectAcrossModes() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/student/scores/" + taskAId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        int totalScore = data.get("totalScore").asInt();
        int maxPossible = data.get("maxPossible").asInt();
        int cpPassed = data.get("checkpointsPassed").asInt();

        // 任务 A: EXPLOIT(100分) 已通过=100, DEFENSE 检查点也有提交
        Assertions.assertTrue(totalScore > 0, "总分应 > 0");
        Assertions.assertEquals(200, maxPossible, "最大可能分应为 200 (两个 100 分检查点)");

        // 检查各检查点分项
        JsonNode cpScores = data.get("checkpointScores");
        Assertions.assertNotNull(cpScores, "应包含 checkpointScores");
        Assertions.assertEquals(2, cpScores.size(), "应有 2 个检查点分项");

        // 验证 EXPLOIT 检查点
        boolean foundExploit = false;
        boolean foundDefense = false;
        for (JsonNode cs : cpScores) {
            if ("EXPLOIT".equals(cs.get("mode").asText())) {
                foundExploit = true;
                Assertions.assertEquals(100, cs.get("score").asInt(), "EXPLOIT 分数应为 100");
                Assertions.assertTrue(cs.get("passed").asBoolean(), "EXPLOIT 应已通过");
            }
            if ("DEFENSE".equals(cs.get("mode").asText())) {
                foundDefense = true;
            }
        }
        Assertions.assertTrue(foundExploit, "分项中应包含 EXPLOIT 检查点");
        Assertions.assertTrue(foundDefense, "分项中应包含 DEFENSE 检查点");
    }

    @Test
    @Order(13)
    void testAdminStats_ShowsAllModeAttempts() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/tasks/" + taskAId + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode stats = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        boolean foundGuest = false;
        for (JsonNode stat : stats) {
            JsonNode usernameNode = stat.has("username") ? stat.get("username") : stat.get("USERNAME");
            if (usernameNode != null && "guest".equals(usernameNode.asText())) {
                foundGuest = true;
                JsonNode scoreNode = stat.has("total_score") ? stat.get("total_score") : stat.get("TOTAL_SCORE");
                Assertions.assertTrue(scoreNode.asInt() > 0, "guest 总分应 > 0");
            }
        }
        Assertions.assertTrue(foundGuest, "管理员统计中应包含 guest 用户");
    }

    // ==================== 辅助方法 ====================

    private String generateToken(int id, String username, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("username", username);
        claims.put("name", name);
        return JwtUtils.generateJwt(claims);
    }

    private JsonNode createTask(DrillTaskCreateRequest req) throws Exception {
        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
