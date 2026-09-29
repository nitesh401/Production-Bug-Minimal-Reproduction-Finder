package com.example.reproduction.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SimulatorControllerTest {
    @Autowired MockMvc mvc;

    private static final String FULL = "{\"amount\":15000,\"currency\":\"INR\",\"customerType\":\"PREMIUM\",\"featureFlags\":{\"FAST_PATH\":true}}";

    @Test
    void buggyInputReturns500WithErrorCode() throws Exception {
        mvc.perform(post("/simulate/payment").header("X-Bug-Scenario", "SIMPLE_AND").contentType(MediaType.APPLICATION_JSON).content(FULL))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("PAYMENT_ROUTE_FAILURE"));
    }

    @Test
    void reducedInputReturns200() throws Exception {
        mvc.perform(post("/simulate/payment").header("X-Bug-Scenario", "SIMPLE_AND").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":15000,\"currency\":\"INR\"}")).andExpect(status().isOk());
    }

    @Test
    void customScenarioCanBeRegisteredAndListed() throws Exception {
        String body = "{\"id\":\"CUSTOM_1\",\"rules\":[{\"anyOf\":[[{\"path\":\"a\",\"op\":\"EQ\",\"value\":1}]],\"status\":500,\"errorCode\":\"X\"}]}";
        mvc.perform(post("/api/v1/simulator/scenarios").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/simulator/scenarios")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='CUSTOM_1')]").exists());
        mvc.perform(post("/simulate/payment").header("X-Bug-Scenario", "CUSTOM_1").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void builtInScenarioCannotBeOverwritten() throws Exception {
        String body = "{\"id\":\"SIMPLE_AND\",\"rules\":[{\"anyOf\":[[{\"path\":\"a\",\"op\":\"EQ\",\"value\":1}]],\"status\":500}]}";
        mvc.perform(post("/api/v1/simulator/scenarios").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }
}
