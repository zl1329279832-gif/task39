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
 * 演练系统 Bug 修复测试
 * 覆盖:
 *  1. 任务创建验证
 *  2. safe/vulnerable (EXPLOIT/DEFENSE) 模式证据隔离
 *  3. 检查点版本变更使缓存失效
 *  4. 重复提交幂等（不重复加分）
 *  5. 重试扣分正确性
 *  6. 提示扣分和耗时扣分可追溯
 *  7. 管理员调整任务后旧实例不被污染
 *  8. 权限绕过拦截
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
    private String user2Token;

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

    // ==================== 1. 任务创建测试 ====================

    @Test
    @Order(1)
    void testCreateTask_WithMultipleModes_Success() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("模式隔离测试任务");
        req.setDescription("包含 EXPLOIT 和 DEFENSE 两种模式的检查点");
        req.setDifficulty("medium");

        List<DrillTaskCreateRequest.CheckpointDef> checkpoints = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
        cp1.setVulnCategory("SQLI_NUMERIC");
        cp1.setCheckpointOrder(1);
        cp1.setMode("EXPLOIT");
        cp1.setMaxScore(100);
        cp1.setMaxHints(2);
        cp1.setTimeLimit(1800);
        checkpoints.add(cp1);

        DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
        cp2.setVulnCategory("PATH_TRAVERSAL");
        cp2.setCheckpointOrder(2);
        cp2.setMode("DEFENSE");
        cp2.setMaxScore(80);
        cp2.setMaxHints(3);
        cp2.setTimeLimit(1200);
        checkpoints.add(cp2);

        req.setCheckpoints(checkpoints);

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").exists())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(2))
                .andExpect(jsonPath("$.data.checkpoints[0].mode").value("EXPLOIT"))
                .andExpect(jsonPath("$.data.checkpoints[1].mode").value("DEFENSE"))
                .andReturn();
    }

    @Test
    @Order(2)
    void testCreateTask_InvalidCategory_Rejected() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("无效类别任务");
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("INVALID_CATEGORY");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        req.setCheckpoints(Collections.singletonList(cp));

        mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 2. Safe/Vulnerable 模式隔离测试 ====================

    @Test
    @Order(10)
    void testExploitMode_EvidenceValidated() throws Exception {
        // 创建纯 EXPLOIT 任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("EXPLOIT模式测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 提交正确的 EXPLOIT 证据
        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(taskId);
        submit.setCheckpointId(cpId);
        submit.setPayloadSummary("id=1 OR 1=1");
        submit.setEvidence("[{\"id\":1,\"username\":\"zhangsan\",\"password\":\"secret\"}]");
        submit.setElapsedSeconds(100);
        submit.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.checkpointMode").value("EXPLOIT"));
    }

    @Test
    @Order(11)
    void testDefenseMode_IgnoresEvidenceField() throws Exception {
        // 创建纯 DEFENSE 任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("DEFENSE模式测试", "PATH_TRAVERSAL", "DEFENSE");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // DEFENSE 模式：evidence 字段应被忽略，仅用 payloadSummary 重放到安全端点
        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(taskId);
        submit.setCheckpointId(cpId);
        submit.setPayloadSummary("../../../etc/passwd");
        submit.setEvidence("这个证据内容在DEFENSE模式下应该被忽略");
        submit.setElapsedSeconds(100);
        submit.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resultJson = objectMapper.readTree(result.getResponse().getContentAsString());
        // DEFENSE 模式不检查 evidence，而是重放 payload 到安全端点
        Assertions.assertEquals("DEFENSE",
                resultJson.get("data").get("checkpointMode").asText(),
                "DEFENSE 模式的提交应记录 checkpointMode=DEFENSE");
    }

    @Test
    @Order(12)
    void testMixedModeTask_EachCheckpointIsolated() throws Exception {
        // 创建包含 EXPLOIT + DEFENSE 的混合任务
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("混合模式隔离测试");
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
        cp2.setVulnCategory("PATH_TRAVERSAL");
        cp2.setCheckpointOrder(2);
        cp2.setMode("DEFENSE");
        cp2.setMaxScore(100);
        cp2.setMaxHints(3);
        cp2.setTimeLimit(1800);
        cps.add(cp2);

        req.setCheckpoints(cps);
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int exploitCpId = json.get("data").get("checkpoints").get(0).get("id").asInt();
        int defenseCpId = json.get("data").get("checkpoints").get(1).get("id").asInt();

        // 通过 EXPLOIT 检查点
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(exploitCpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"admin\",\"password\":\"leaked\"}]");
        s1.setElapsedSeconds(60);
        s1.setHintsUsed(0);

        MvcResult r1 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.checkpointMode").value("EXPLOIT"))
                .andReturn();

        // DEFENSE 检查点不应受到 EXPLOIT 结果的影响
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(defenseCpId);
        s2.setPayloadSummary("../../etc/passwd");
        s2.setEvidence("与 EXPLOIT 相同的证据内容");
        s2.setElapsedSeconds(60);
        s2.setHintsUsed(0);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        Assertions.assertEquals("DEFENSE",
                r2Json.get("data").get("checkpointMode").asText(),
                "DEFENSE 检查点应有独立的 mode 标记");
        Assertions.assertEquals(defenseCpId,
                r2Json.get("data").get("checkpointId").asInt(),
                "DEFENSE 检查点 ID 应与 EXPLOIT 不同");
    }

    // ==================== 3. 检查点版本变更使缓存失效 ====================

    @Test
    @Order(20)
    void testCheckpointVersionChange_InvalidatesCache() throws Exception {
        // 创建任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("版本变更测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 首次通过
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"leaked\"}]");
        s1.setElapsedSeconds(60);
        s1.setHintsUsed(0);

        MvcResult r1 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.checkpointVersion").value(1))
                .andReturn();

        // 管理员更新检查点（触发版本号 +1）
        Map<String, Object> update = new HashMap<>();
        update.put("vulnCategory", "SQLI_NUMERIC");
        update.put("mode", "EXPLOIT");
        update.put("maxScore", 100);
        update.put("maxHints", 3);
        update.put("timeLimit", 1800);
        update.put("verifyPattern", "completely_different_pattern_that_wont_match");
        update.put("defensePattern", "blocked");
        update.put("hintContent", null);

        mockMvc.perform(put("/drill/admin/checkpoints/" + cpId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // 再次提交：版本已变更，缓存应失效，需要重新验证
        // 由于 verifyPattern 已改为不匹配的模式，原来的证据不再有效
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"leaked\"}]");
        s2.setElapsedSeconds(60);
        s2.setHintsUsed(0);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        // 新的验证模式不匹配，所以应判为失败
        Assertions.assertEquals(0, r2Json.get("data").get("passed").asInt(),
                "版本变更后旧证据不应自动通过");
        Assertions.assertEquals(2, r2Json.get("data").get("checkpointVersion").asInt(),
                "新版本号应为 2");
    }

    // ==================== 4. 重复提交幂等（不重复加分） ====================

    @Test
    @Order(30)
    void testDuplicateSubmission_Idempotent_NoDoubleScore() throws Exception {
        // 创建任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("幂等性测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 首次通过
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"leaked\"}]");
        s1.setElapsedSeconds(100);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100));

        // 第二次提交（不同 payload/evidence），应返回相同的缓存结果
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("完全不同的 payload");
        s2.setEvidence("完全不同的证据");
        s2.setElapsedSeconds(999);
        s2.setHintsUsed(10);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andReturn();

        // 验证分数摘要：不应重复加分
        mockMvc.perform(get("/drill/student/scores/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(100))
                .andExpect(jsonPath("$.data.checkpointsPassed").value(1));
    }

    @Test
    @Order(31)
    void testDuplicateSubmission_DifferentUsers_Isolated() throws Exception {
        // 创建任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("多用户幂等测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 用户 guest 通过
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"guest_leaked\"}]");
        s1.setElapsedSeconds(100);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1));

        // 用户 zhangsan 独立提交（不应受 guest 影响）
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"zhangsan_leaked\"}]");
        s2.setElapsedSeconds(100);
        s2.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    // ==================== 5. 重试扣分正确性 ====================

    @Test
    @Order(40)
    void testRetryPenalty_AppliedOnFailedThenPassed() throws Exception {
        // 创建任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("重试扣分测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 第一次：失败
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("正常查询");
        s1.setEvidence("没有任何漏洞利用结果");
        s1.setElapsedSeconds(60);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(0))
                .andExpect(jsonPath("$.data.score").value(0));

        // 第二次：通过，应有重试扣分 3 分
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"leaked\"}]");
        s2.setElapsedSeconds(120);
        s2.setHintsUsed(0);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(97)) // 100 - 3(重试)
                .andReturn();

        // 验证扣分项包含"重试"
        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        String deductions = r2Json.get("data").get("deductionItems").asText();
        Assertions.assertTrue(deductions.contains("attemptNumber"), "扣分项应包含 attemptNumber");
        Assertions.assertTrue(deductions.contains("3"), "扣分项应包含重试扣分值 3");
        Assertions.assertTrue(deductions.contains("reason"), "扣分项应包含 reason 字段");
    }

    @Test
    @Order(41)
    void testRetryPenalty_MultipleRetries_IncrementingNumber() throws Exception {
        DrillTaskCreateRequest req = createSingleCheckpointTask("多次重试测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 连续失败 2 次
        for (int i = 0; i < 2; i++) {
            DrillSubmitRequest s = new DrillSubmitRequest();
            s.setTaskId(taskId);
            s.setCheckpointId(cpId);
            s.setPayloadSummary("正常查询");
            s.setEvidence("失败");
            s.setElapsedSeconds(60);
            s.setHintsUsed(0);

            mockMvc.perform(post("/drill/student/submit")
                            .header("Authorization", guestToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(s)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passed").value(0));
        }

        // 第 3 次通过
        DrillSubmitRequest s3 = new DrillSubmitRequest();
        s3.setTaskId(taskId);
        s3.setCheckpointId(cpId);
        s3.setPayloadSummary("OR 1=1");
        s3.setEvidence("[{\"username\":\"leaked\"}]");
        s3.setElapsedSeconds(200);
        s3.setHintsUsed(0);

        MvcResult r3 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(97)) // 100 - 3(重试)
                .andExpect(jsonPath("$.data.attemptNumber").value(3)) // 第 3 次
                .andReturn();
    }

    // ==================== 6. 提示扣分和耗时扣分可追溯 ====================

    @Test
    @Order(50)
    void testHintDeduction_Traceable() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("提示扣分追溯测试");
        req.setDifficulty("hard");
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_UNION");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(1);
        cp.setTimeLimit(1800);
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 使用 4 次提示（超过 maxHints=1），应扣 (4-1)*5 = 15 分
        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(taskId);
        submit.setCheckpointId(cpId);
        submit.setPayloadSummary("UNION SELECT 1,username,password,4 FROM admin");
        submit.setEvidence("{\"title\":\"article\",\"author\":\"admin\"}");
        submit.setElapsedSeconds(300);
        submit.setHintsUsed(4);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(85)) // 100 - 15(提示)
                .andExpect(jsonPath("$.data.maxHintsSnapshot").value(1))
                .andReturn();

        // 验证扣分项详细记录
        JsonNode resultJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String deductions = resultJson.get("data").get("deductionItems").asText();
        Assertions.assertTrue(deductions.contains("reason"), "扣分项应包含 reason 字段");
        Assertions.assertTrue(deductions.contains("\"hintsUsed\":4"), "扣分项应记录实际使用次数");
        Assertions.assertTrue(deductions.contains("\"maxHints\":1"), "扣分项应记录最大允许次数");
        Assertions.assertTrue(deductions.contains("15"), "扣分项应包含扣分值 15");
    }

    @Test
    @Order(51)
    void testTimeDeduction_Traceable() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("耗时扣分追溯测试");
        req.setDifficulty("easy");
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_NUMERIC");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(5);
        cp.setTimeLimit(60); // 60 秒限制
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 超时 120 秒（超过限制 60 秒），扣分 = min(20, (120-60)/60*5) = min(20, 5) = 5
        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(taskId);
        submit.setCheckpointId(cpId);
        submit.setPayloadSummary("OR 1=1");
        submit.setEvidence("[{\"username\":\"leaked\"}]");
        submit.setElapsedSeconds(120);
        submit.setHintsUsed(0);

        MvcResult result = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(95)) // 100 - 5(超时)
                .andExpect(jsonPath("$.data.timeLimitSnapshot").value(60))
                .andReturn();

        JsonNode resultJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String deductions = resultJson.get("data").get("deductionItems").asText();
        Assertions.assertTrue(deductions.contains("reason"), "扣分项应包含 reason 字段");
        Assertions.assertTrue(deductions.contains("\"timeLimit\":60"), "扣分项应记录时间限制");
        Assertions.assertTrue(deductions.contains("\"elapsedSeconds\":120"), "扣分项应记录实际耗时");
    }

    @Test
    @Order(52)
    void testCombinedDeductions_HintsAndTime() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("综合扣分测试");
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_NUMERIC");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(1);
        cp.setTimeLimit(60);
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 先失败一次
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("normal");
        s1.setEvidence("fail");
        s1.setElapsedSeconds(30);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(0));

        // 然后超时 + 超提示 + 重试
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"leaked\"}]");
        s2.setElapsedSeconds(180); // 超时 120 秒
        s2.setHintsUsed(3); // 超提示 2 次

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andReturn();

        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        int score = r2Json.get("data").get("score").asInt();
        // 扣分：提示 (3-1)*5=10 + 超时 min(20,(180-60)/60*5)=min(20,10)=10 + 重试 3 = 23
        Assertions.assertEquals(77, score, "综合扣分: 100 - 10(提示) - 10(超时) - 3(重试) = 77");

        // 验证快照字段
        Assertions.assertEquals(100, r2Json.get("data").get("maxScoreSnapshot").asInt());
        Assertions.assertEquals(1, r2Json.get("data").get("maxHintsSnapshot").asInt());
        Assertions.assertEquals(60, r2Json.get("data").get("timeLimitSnapshot").asInt());
    }

    // ==================== 7. 管理员调整任务后旧实例不被污染 ====================

    @Test
    @Order(60)
    void testAdminUpdateTask_OldAttemptsIsolated() throws Exception {
        // 创建任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("管理员修改隔离测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 学员通过检查点
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"leaked\"}]");
        s1.setElapsedSeconds(60);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.checkpointVersion").value(1));

        // 管理员修改检查点（降低满分）
        Map<String, Object> update = new HashMap<>();
        update.put("vulnCategory", "SQLI_NUMERIC");
        update.put("mode", "EXPLOIT");
        update.put("maxScore", 50); // 改为 50 分
        update.put("maxHints", 3);
        update.put("timeLimit", 1800);
        update.put("verifyPattern", "\"username\"");
        update.put("defensePattern", "error");
        update.put("hintContent", null);

        mockMvc.perform(put("/drill/admin/checkpoints/" + cpId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // 旧尝试的分数不应被新规则影响（快照保存）
        mockMvc.perform(get("/drill/student/attempts/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].maxScoreSnapshot").value(100))
                .andExpect(jsonPath("$.data[0].checkpointVersion").value(1));

        // 重新提交：版本已变更，缓存失效，新提交使用新的满分 50
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"leaked_again\"}]");
        s2.setElapsedSeconds(60);
        s2.setHintsUsed(0);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        Assertions.assertEquals(2, r2Json.get("data").get("checkpointVersion").asInt(),
                "新版本应为 2");
        Assertions.assertEquals(50, r2Json.get("data").get("maxScoreSnapshot").asInt(),
                "新的快照应使用新的满分 50");
    }

    @Test
    @Order(61)
    void testAdminModeSwitch_InvalidatesOldResult() throws Exception {
        // 创建 EXPLOIT 模式任务
        DrillTaskCreateRequest req = createSingleCheckpointTask("模式切换测试", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cpId = json.get("data").get("checkpoints").get(0).get("id").asInt();

        // 学员以 EXPLOIT 模式通过
        DrillSubmitRequest s1 = new DrillSubmitRequest();
        s1.setTaskId(taskId);
        s1.setCheckpointId(cpId);
        s1.setPayloadSummary("OR 1=1");
        s1.setEvidence("[{\"username\":\"leaked\"}]");
        s1.setElapsedSeconds(60);
        s1.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.checkpointMode").value("EXPLOIT"));

        // 管理员把模式改为 DEFENSE（同时 bump 版本）
        Map<String, Object> update = new HashMap<>();
        update.put("vulnCategory", "SQLI_NUMERIC");
        update.put("mode", "DEFENSE");
        update.put("maxScore", 100);
        update.put("maxHints", 3);
        update.put("timeLimit", 1800);
        update.put("verifyPattern", "\"username\"");
        update.put("defensePattern", "error|查询失败");
        update.put("hintContent", null);

        mockMvc.perform(put("/drill/admin/checkpoints/" + cpId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // 再次提交：旧 EXPLOIT 缓存失效
        DrillSubmitRequest s2 = new DrillSubmitRequest();
        s2.setTaskId(taskId);
        s2.setCheckpointId(cpId);
        s2.setPayloadSummary("OR 1=1");
        s2.setEvidence("[{\"username\":\"leaked\"}]");
        s2.setElapsedSeconds(60);
        s2.setHintsUsed(0);

        MvcResult r2 = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s2)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode r2Json = objectMapper.readTree(r2.getResponse().getContentAsString());
        Assertions.assertEquals("DEFENSE",
                r2Json.get("data").get("checkpointMode").asText(),
                "模式切换后新提交应使用 DEFENSE 模式");
        Assertions.assertEquals(2,
                r2Json.get("data").get("checkpointVersion").asInt(),
                "版本应更新为 2");
    }

    // ==================== 8. 权限绕过拦截测试 ====================

    @Test
    @Order(70)
    void testGuestCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(71)
    void testGuestCannotUpdateCheckpoint() throws Exception {
        Map<String, Object> update = new HashMap<>();
        update.put("mode", "EXPLOIT");

        mockMvc.perform(put("/drill/admin/checkpoints/1")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(72)
    void testGuestCannotResetTask() throws Exception {
        mockMvc.perform(post("/drill/admin/tasks/1/reset")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(73)
    void testGuestCannotViewStats() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/1/stats")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(74)
    void testUnauthenticatedRequest_Rejected() throws Exception {
        mockMvc.perform(get("/drill/student/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(75)
    void testInvalidToken_Rejected() throws Exception {
        mockMvc.perform(get("/drill/student/tasks")
                        .header("Authorization", "invalid_token_here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(76)
    void testTaskIdMismatch_Rejected() throws Exception {
        // 创建两个不同的任务
        DrillTaskCreateRequest req1 = createSingleCheckpointTask("任务A", "SQLI_NUMERIC", "EXPLOIT");
        MvcResult r1 = createTaskAsAdmin(req1);
        JsonNode j1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        int taskId1 = j1.get("data").get("task").get("id").asInt();
        int cpId1 = j1.get("data").get("checkpoints").get(0).get("id").asInt();

        DrillTaskCreateRequest req2 = createSingleCheckpointTask("任务B", "SQLI_STRING", "EXPLOIT");
        MvcResult r2 = createTaskAsAdmin(req2);
        JsonNode j2 = objectMapper.readTree(r2.getResponse().getContentAsString());
        int taskId2 = j2.get("data").get("task").get("id").asInt();

        // 用任务 B 的 taskId 提交任务 A 的 checkpointId
        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(taskId2); // 任务 B
        submit.setCheckpointId(cpId1); // 任务 A 的检查点
        submit.setPayloadSummary("OR 1=1");
        submit.setEvidence("[{\"username\":\"leaked\"}]");
        submit.setElapsedSeconds(60);
        submit.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    // ==================== 9. 成绩统计测试 ====================

    @Test
    @Order(80)
    void testAdminStats_ReflectCorrectScores() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("统计测试任务");
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
        cp2.setVulnCategory("SQLI_STRING");
        cp2.setCheckpointOrder(2);
        cp2.setMode("EXPLOIT");
        cp2.setMaxScore(80);
        cp2.setMaxHints(3);
        cp2.setTimeLimit(1800);
        cps.add(cp2);

        req.setCheckpoints(cps);
        MvcResult createResult = createTaskAsAdmin(req);
        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int taskId = json.get("data").get("task").get("id").asInt();
        int cp1Id = json.get("data").get("checkpoints").get(0).get("id").asInt();
        int cp2Id = json.get("data").get("checkpoints").get(1).get("id").asInt();

        // guest 通过两个检查点
        submitExploit(guestToken, taskId, cp1Id, "[{\"username\":\"g1\"}]", 60, 0);
        submitExploit(guestToken, taskId, cp2Id, "{\"password\":\"g2\"}", 60, 0);

        // 管理员查看统计
        MvcResult statsResult = mockMvc.perform(get("/drill/admin/tasks/" + taskId + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode stats = objectMapper.readTree(statsResult.getResponse().getContentAsString()).get("data");
        boolean foundGuest = false;
        for (JsonNode stat : stats) {
            JsonNode usernameNode = stat.has("username") ? stat.get("username") : stat.get("USERNAME");
            if (usernameNode != null && "guest".equals(usernameNode.asText())) {
                foundGuest = true;
                JsonNode scoreNode = stat.has("total_score") ? stat.get("total_score") : stat.get("TOTAL_SCORE");
                Assertions.assertEquals(180, scoreNode.asInt(), "guest 总分应为 180");
            }
        }
        Assertions.assertTrue(foundGuest, "统计中应包含 guest 用户");
    }

    // ==================== Helper Methods ====================

    private DrillTaskCreateRequest createSingleCheckpointTask(
            String title, String vulnCategory, String mode) {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle(title);
        req.setDifficulty("medium");
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory(vulnCategory);
        cp.setCheckpointOrder(1);
        cp.setMode(mode);
        cp.setMaxScore(100);
        cp.setMaxHints(3);
        cp.setTimeLimit(1800);
        req.setCheckpoints(Collections.singletonList(cp));
        return req;
    }

    private MvcResult createTaskAsAdmin(DrillTaskCreateRequest req) throws Exception {
        return mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    private void submitExploit(String token, int taskId, int cpId,
                                String evidence, int elapsed, int hints) throws Exception {
        DrillSubmitRequest s = new DrillSubmitRequest();
        s.setTaskId(taskId);
        s.setCheckpointId(cpId);
        s.setPayloadSummary("payload");
        s.setEvidence(evidence);
        s.setElapsedSeconds(elapsed);
        s.setHintsUsed(hints);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1));
    }
}
