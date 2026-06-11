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
 * 演练成绩统计测试
 * 验证分数聚合、扣分项追踪和管理员统计视图
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillScoreStatisticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;
    private Integer taskId;
    private Integer cp1Id;
    private Integer cp2Id;

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

        try {
            DrillTaskCreateRequest req = new DrillTaskCreateRequest();
            req.setTitle("成绩统计测试任务");
            req.setDescription("用于验证分数聚合和扣分");
            req.setDifficulty("easy");

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
            cp2.setVulnCategory("SQLI_STRING");
            cp2.setCheckpointOrder(2);
            cp2.setMode("EXPLOIT");
            cp2.setMaxScore(80);
            cp2.setMaxHints(2);
            cp2.setTimeLimit(1800);
            checkpoints.add(cp2);

            req.setCheckpoints(checkpoints);

            MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            taskId = json.get("data").get("task").get("id").asInt();
            cp1Id = json.get("data").get("checkpoints").get(0).get("id").asInt();
            cp2Id = json.get("data").get("checkpoints").get(1).get("id").asInt();
        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }
    }

    @Test
    @Order(1)
    void testSubmitCheckpoint1_GuestPasses() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(cp1Id);
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"zhangsan\"},{\"id\":2,\"username\":\"lisi\"}]");
        req.setElapsedSeconds(120);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    @Test
    @Order(2)
    void testSubmitCheckpoint2_GuestPasses() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(cp2Id);
        req.setPayloadSummary("username=admin' OR '1'='1");
        req.setEvidence("{\"username\":\"admin\",\"password\":\"secret123\",\"role\":\"admin\"}");
        req.setElapsedSeconds(200);
        req.setHintsUsed(1);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(80));
    }

    @Test
    @Order(3)
    void testScoreSummary_CorrectAggregation() throws Exception {
        mockMvc.perform(get("/drill/student/scores/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalScore").value(180))
                .andExpect(jsonPath("$.data.maxPossible").value(180))
                .andExpect(jsonPath("$.data.checkpointsPassed").value(2))
                .andExpect(jsonPath("$.data.checkpointsTotal").value(2))
                .andExpect(jsonPath("$.data.completionPct").value(100.0));
    }

    @Test
    @Order(4)
    void testAdminViewTaskStats() throws Exception {
        MvcResult result = mockMvc.perform(get("/drill/admin/tasks/" + taskId + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNotEmpty())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode stats = objectMapper.readTree(json).get("data");
        boolean foundGuest = false;
        for (JsonNode stat : stats) {
            // H2 可能将列名转为大写，需要同时检查大小写
            JsonNode usernameNode = stat.has("username") ? stat.get("username") : stat.get("USERNAME");
            if (usernameNode != null && "guest".equals(usernameNode.asText())) {
                foundGuest = true;
                JsonNode scoreNode = stat.has("total_score") ? stat.get("total_score") : stat.get("TOTAL_SCORE");
                Assertions.assertNotNull(scoreNode, "total_score 不应为空");
                Assertions.assertTrue(scoreNode.asInt() >= 180,
                        "guest 的总分应 >= 180");
            }
        }
        Assertions.assertTrue(foundGuest, "统计中应包含 guest 用户");
    }

    @Test
    @Order(5)
    void testScoreDeduction_ExcessHints() throws Exception {
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("扣分测试任务");
        req.setDifficulty("hard");

        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_UNION");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(1);
        cp.setTimeLimit(1800);
        req.setCheckpoints(Collections.singletonList(cp));

        MvcResult createResult = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int deductionCpId = createJson.get("data").get("checkpoints").get(0).get("id").asInt();
        int deductionTaskId = createJson.get("data").get("task").get("id").asInt();

        DrillSubmitRequest submit = new DrillSubmitRequest();
        submit.setTaskId(deductionTaskId);
        submit.setCheckpointId(deductionCpId);
        submit.setPayloadSummary("UNION SELECT 1,username,password,4 FROM admin");
        submit.setEvidence("{\"id\":1,\"title\":\"article\",\"author\":\"admin\",\"content\":\"leaked data\"}");
        submit.setElapsedSeconds(300);
        submit.setHintsUsed(3);

        MvcResult submitResult = mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(1))
                .andExpect(jsonPath("$.data.score").value(90))
                .andReturn();

        String submitJson = submitResult.getResponse().getContentAsString();
        String deductionItems = objectMapper.readTree(submitJson)
                .get("data").get("deductionItems").asText();
        // 验证扣分项包含扣分值 10（(3-1)*5=10）
        Assertions.assertTrue(deductionItems.contains("10"),
                "扣分项应包含扣分值 10");
        Assertions.assertTrue(deductionItems.contains("reason"),
                "扣分项应包含 reason 字段");
    }
}
