package com.example.reproduction.api;

import com.example.reproduction.api.application.JobService;
import com.example.reproduction.api.web.JobController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerValidationTest {
    @Autowired MockMvc mvc;
    @MockBean JobService service;

    @Test
    void blankNameIsRejectedWith400() throws Exception {
        String body = "{\"name\":\"\",\"initialInput\":{\"a\":1},\"bugSignature\":{\"httpStatus\":500}}";
        mvc.perform(post("/api/v1/reproduction/jobs").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void emptyInitialInputIsRejectedWith400() throws Exception {
        String body = "{\"name\":\"x\",\"initialInput\":{},\"bugSignature\":{\"httpStatus\":500}}";
        mvc.perform(post("/api/v1/reproduction/jobs").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void missingBugSignatureIsRejectedWith400() throws Exception {
        String body = "{\"name\":\"x\",\"initialInput\":{\"a\":1}}";
        mvc.perform(post("/api/v1/reproduction/jobs").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeReproductionRateIsRejected() throws Exception {
        String body = "{\"name\":\"x\",\"initialInput\":{\"a\":1},\"bugSignature\":{\"httpStatus\":500},\"minimumReproductionRate\":1.5}";
        mvc.perform(post("/api/v1/reproduction/jobs").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
}
