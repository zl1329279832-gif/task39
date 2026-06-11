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
public class DrillAttemptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String guestToken;
    private Integer taskId;
    private Integer sqliCheckpointId;
    private Integer xxeCheckpointId;
    private Integer defenseCheckpointId;

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

        // Create a task with 3 checkpoints
        DrillTaskCreateRequest req = new DrillTaskCreateRequest();
        req.setTitle("综合漏洞演练");
        req.setDescription("SQLI + XXE + PATH_TRAVERSAL");
        req.setDifficulty("medium");

        List<DrillTaskCreateRequest.CheckpointDef> cps = new ArrayList<>();

        DrillTaskCreateRequest.CheckpointDef cp1 = new DrillTaskCreateRequest.CheckpointDef();
        cp1.setVulnCategory("SQLI_NUMERIC");
        cp1.setCheckpointOrder(1);
        cp1.setMode("EXPLOIT");
        cp1.setMaxScore(100);
        cp1.setMaxHints(3);
        cps.add(cp1);

        DrillTaskCreateRequest.CheckpointDef cp2 = new DrillTaskCreateRequest.CheckpointDef();
        cp2.setVulnCategory("XXE_BASIC");
        cp2.setCheckpointOrder(2);
        cp2.setMode("EXPLOIT");
        cp2.setMaxScore(100);
        cp2.setMaxHints(3);
        cps.add(cp2);

        DrillTaskCreateRequest.CheckpointDef cp3 = new DrillTaskCreateRequest.CheckpointDef();
        cp3.setVulnCategory("PATH_TRAVERSAL");
        cp3.setCheckpointOrder(3);
        cp3.setMode("DEFENSE");
        cp3.setMaxScore(100);
        cp3.setMaxHints(3);
        cp3.setVulnEndpoint("/pathtraversal/vuln1");
        cp3.setSecEndpoint("/pathtraversal/sec1");
        cp3.setDefensePattern("path.*validation|canonical");
        cps.add(cp3);

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
        sqliCheckpointId = (Integer) checkpoints.get(0).get("id");
        xxeCheckpointId = (Integer) checkpoints.get(1).get("id");
        defenseCheckpointId = (Integer) checkpoints.get(2).get("id");
    }

    @Test
    void testGuestStartTask_GetsCheckpoints() throws Exception {
        mockMvc.perform(get("/drill/student/start/" + taskId)
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.checkpoints").isArray())
                .andExpect(jsonPath("$.data.checkpoints.length()").value(3))
                .andExpect(jsonPath("$.data.checkpoints[0].vulnEndpoint").exists())
                .andExpect(jsonPath("$.data.checkpoints[0].secEndpoint").exists());
    }

    @Test
    void testSubmitExploitCheckpoint_CorrectEvidence_Passes() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(sqliCheckpointId);
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"zhangsan\",\"password\":\"123\"},{\"id\":2,\"username\":\"lisi\",\"password\":\"123\"}]");
        req.setElapsedSeconds(120);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.score").value(100));
    }

    @Test
    void testSubmitExploitCheckpoint_WrongEvidence_Fails() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(xxeCheckpointId);
        req.setPayloadSummary("random payload");
        req.setEvidence("no matching content here");
        req.setElapsedSeconds(60);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.score").value(0));
    }

    @Test
    void testIdempotentSubmission_SameResultOnRetry() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(sqliCheckpointId);
        req.setPayloadSummary("id=1 OR 1=1");
        req.setEvidence("[{\"id\":1,\"username\":\"admin\",\"password\":\"123456\"}]");
        req.setElapsedSeconds(150);
        req.setHintsUsed(0);

        // First submission
        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Second submission (idempotent update)
        req.setElapsedSeconds(200);
        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void testSubmitXxeCheckpoint_WithCorrectEvidence() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(xxeCheckpointId);
        req.setPayloadSummary("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>");
        req.setEvidence("root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1:daemon:/usr/sbin");
        req.setElapsedSeconds(300);
        req.setHintsUsed(1);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.score").value(90));
    }

    @Test
    void testDefenseMode_IgnoresEvidenceField() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(defenseCheckpointId);
        req.setPayloadSummary("used secure endpoint /pathtraversal/sec1");
        req.setEvidence("../../../etc/passwd");
        req.setElapsedSeconds(60);
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
    void testGuestCanAttemptDrills() throws Exception {
        mockMvc.perform(get("/drill/student/tasks")
                        .header("Authorization", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void testGuestCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/drill/admin/tasks")
                        .header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminCanAlsoAttemptDrills() throws Exception {
        DrillSubmitRequest req = new DrillSubmitRequest();
        req.setTaskId(taskId);
        req.setCheckpointId(defenseCheckpointId);
        req.setPayloadSummary("admin testing defense mode");
        req.setEvidence("secure endpoint used");
        req.setElapsedSeconds(30);
        req.setHintsUsed(0);

        mockMvc.perform(post("/drill/student/submit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
