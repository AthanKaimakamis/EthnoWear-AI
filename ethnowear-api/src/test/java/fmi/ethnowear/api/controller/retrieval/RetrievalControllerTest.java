package fmi.ethnowear.api.controller.retrieval;

import fmi.ethnowear.api.exception.RetrievalApiExceptionHandler;
import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalDetails;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.service.retrieval.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RetrievalControllerTest {

    private RagRetrievalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RagRetrievalService.class);
        mockMvc = standaloneSetup(new RetrievalController(service))
                .setControllerAdvice(new RetrievalApiExceptionHandler())
                .build();
    }

    @Test
    void returnsGroundedRetrievalResult() throws Exception {
        when(service.retrieve(any())).thenReturn(
                new GroundedRetrievalDetails(
                        "Какво е шопска шевица?",
                        0,
                        List.of()
                )
        );

        mockMvc.perform(post("/api/admin/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Какво е шопска шевица?",
                                  "resultCount": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(0));
    }

    @Test
    void mapsUnavailableDependencySafely() throws Exception {
        when(service.retrieve(any())).thenThrow(
                new RetrievalUnavailableException(
                        "The vector search service is unavailable"
                )
        );

        mockMvc.perform(post("/api/admin/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"въпрос\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("RETRIEVAL_UNAVAILABLE"));
    }
}
