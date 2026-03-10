package com.athanor.api.scenario;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ScenarioControllerTests {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ScenarioService scenarioService = new ScenarioService(
            new ScenarioGraphValidator(),
            objectMapper
        );
        ScenarioController controller = new ScenarioController(scenarioService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ScenarioExceptionHandler())
            .build();
    }

    @Test
    void postCreatesScenarioWithInitialDraftVersion() throws Exception {
        MvcResult result = mockMvc
            .perform(
                post("/scenarios")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            createRequest(validGraph("start", "terminal"))
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scenarioId").isNotEmpty())
            .andExpect(jsonPath("$.versionCount").value(1))
            .andExpect(jsonPath("$.version.number").value(1))
            .andExpect(jsonPath("$.version.state").value("draft"))
            .andReturn();

        JsonNode body = objectMapper.readTree(
            result.getResponse().getContentAsByteArray()
        );
        UUID scenarioId = UUID.fromString(body.get("scenarioId").textValue());

        mockMvc
            .perform(post("/scenarios/{id}/validate", scenarioId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void putCreatesNewScenarioVersion() throws Exception {
        MvcResult created = mockMvc
            .perform(
                post("/scenarios")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            createRequest(validGraph("start", "terminal"))
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andReturn();

        UUID scenarioId = UUID.fromString(
            objectMapper
                .readTree(created.getResponse().getContentAsByteArray())
                .get("scenarioId")
                .textValue()
        );

        Map<String, Object> updatedGraph = validGraph("entry", "finish");
        mockMvc
            .perform(
                put("/scenarios/{id}", scenarioId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "name",
                                "Updated Scenario Name",
                                "description",
                                "updated description",
                                "graph",
                                updatedGraph
                            )
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Updated Scenario Name"))
            .andExpect(jsonPath("$.versionCount").value(2))
            .andExpect(jsonPath("$.version.number").value(2));
    }

    @Test
    void validateReturnsErrorsForInvalidGraph() throws Exception {
        Map<String, Object> invalidGraph = new LinkedHashMap<>(
            validGraph("start", "terminal")
        );
        invalidGraph.remove("entry_node_id");

        MvcResult created = mockMvc
            .perform(
                post("/scenarios")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            createRequest(invalidGraph)
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andReturn();

        UUID scenarioId = UUID.fromString(
            objectMapper
                .readTree(created.getResponse().getContentAsByteArray())
                .get("scenarioId")
                .textValue()
        );

        mockMvc
            .perform(post("/scenarios/{id}/validate", scenarioId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors[*].code", hasItem("entry_node")));
    }

    @Test
    void validateReturnsNotFoundForMissingScenario() throws Exception {
        mockMvc
            .perform(post("/scenarios/{id}/validate", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }


    private Map<String, Object> createRequest(Map<String, Object> graph) {
        return Map.of(
            "name",
            "Scenario Name",
            "description",
            "Scenario Description",
            "graph",
            graph
        );
    }

    private Map<String, Object> validGraph(
        String entryNodeId,
        String terminalNodeId
    ) {
        return Map.of(
            "id",
            "scenario-id",
            "name",
            "Scenario Graph",
            "version",
            1,
            "entry_node_id",
            entryNodeId,
            "nodes",
            List.of(
                Map.of("id", entryNodeId, "type", "DecisionNode"),
                Map.of("id", terminalNodeId, "type", "TerminalNode")
            ),
            "edges",
            List.of(Map.of("from", entryNodeId, "to", terminalNodeId))
        );
    }
}
