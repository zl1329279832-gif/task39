package icu.secnotes.drill;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DrillScoreStatisticsTest {

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
    void setup() throws Exception {
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

        // Create task with 2 EXPLOIT checkpoints
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("评分统计测试任务");
        req.setDescription("用于验证成绩汇总");
        req.setDifficulty("easy");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef c1 = new DrillTaskCreateRequest.CheckpointDef();
        c1.setVulnCategory("SQLI_NUMERIC");
        c1.setCheckpointOrder(1);
        c1.setMode("EXPLOIT");
        c1.setMaxScore(100);
        c1.setMaxHints(3);
        cps.add(c1);

        DrillTaskCreateRequest.CheckpointDef c2 = new DrillTaskCreateRequest.CheckpointDef();
        c2.setVulnCategory("SQLI_STRING");
        c2.setCheckpointOrder(2);
        c2.setMode("EXPLOIT");
        c2.setMaxScore(100);
        c2.setMaxHints(3);
        cps.add(c2);

        req.setCheckpoints(cps);

        MvcResult result = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> task = (Map<String, Object>) data.get("task");
        taskId = (Integer) task.get("id");

        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) data.get("checkpoints");
        cp1Id = (Integer) checkpoints.get(0).get("id");
        cp2Id = (Integer) checkpoints.get(1).get("id");
    }

    @Test
    @Order(1)
    void testSubmitCheckpoint1_GuestPasses() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(cp1Id);
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"},{\"id\":2,\"username\":\"lisi\",\"password\":\"123\"}]");
        req.setElapsedSeconds(100);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
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
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    @Test
    @Order(3)
    void testScoreSummary_CorrectAggregation() throws Exception {
        mockMvc.perform(get("/drill/student/scores/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalScore").value(200))
                .andExpect(jsonPath("$.data.maxPossible").value(200))
                .andExpect(jsonPath("$.data.checkpointsPassed").value(2))
                .andExpect(jsonPath("$.data.checkpointsTotal").value(2))
                .andExpect(jsonPath("$.data.completionPct").value(100.00));
    }

    @Test
    @Order(4)
    void testAdminViewTaskStats() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks/" + taskId + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(5)
    void testScoreDeduction_ExcessHints() throws Exception {
        // Create a new task for deduction test
        DrillTaskCreateRequest taskReq = new DrillTaskCreateRequest();
        taskReq.setTitle("扣分测试任务");
        taskReq.setDescription("测试提示扣分");
        taskReq.setDifficulty("hard");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();
        DrillTaskCreateRequest.CheckpointDef cp = new DrillTaskCreateRequest.CheckpointDef();
        cp.setVulnCategory("SQLI_UNION");
        cp.setCheckpointOrder(1);
        cp.setMode("EXPLOIT");
        cp.setMaxScore(100);
        cp.setMaxHints(5);
        cps.add(cp);
        taskReq.setCheckpoints(cps);

        MvcResult createResult = mockMvc.perform(post("/drill/admin/tasks")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> createBody = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> createData = (Map<String, Object>) createBody.get("data");
        Map<String, Object> newTask = (Map<String, Object>) createData.get("task");
        Integer newTaskId = (Integer) newTask.get("id");
        List<Map<String, Object>> newCps = (List<Map<String, Object>>) createData.get("checkpoints");
        Integer newCpId = (Integer) newCps.get(0).get("id");

        // Submit with many hints used
        DrillSubmitRequest submitReq = new DrillSubmitRequest();
        submitReq.setTaskId(newTaskId);
        submitReq.setCheckpointId(newCpId);
        submitReq.setPayloadSummary("id=1 UNION SELECT 1,2,3,4");
        submitReq.setEvidence("UNION select username,password from users");
        submitReq.setElapsedSeconds(600);
        submitReq.setHintsUsed(3);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.score").value(70))
                .andExpect(jsonPath("$.data.deductionItems").exists());
    }
}
