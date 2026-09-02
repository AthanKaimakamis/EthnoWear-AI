package fmi.ethnowear.api.controller.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionContent;
import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionDetails;
import fmi.ethnowear.application.service.ontology.version.OntologyVersionService;
import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyVersionControllerTest {

    private OntologyVersionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OntologyVersionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OntologyVersionController(service))
                .build();
    }

    @Test
    void returnsLatestVersionMetadata() throws Exception {
        when(service.getLatest()).thenReturn(details());

        mockMvc.perform(get("/api/admin/ontology/versions/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(service).getLatest();
    }

    @Test
    void downloadsSelectedVersionAsUtf8RdfXml() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rdf:RDF/>";
        when(service.getContent(3L)).thenReturn(
                new OntologyVersionContent(3L, 3, "EthnoWear.owx", xml)
        );

        mockMvc.perform(get("/api/admin/ontology/versions/3/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/rdf+xml"))
                .andExpect(content().string(xml))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("EthnoWear-v3.owx")
                ))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        verify(service).getContent(3L);
    }

    @Test
    void downloadsLatestActiveVersion() throws Exception {
        when(service.getLatestContent()).thenReturn(
                new OntologyVersionContent(3L, 3, "EthnoWear.owx", "<rdf:RDF/>")
        );

        mockMvc.perform(get("/api/admin/ontology/versions/latest/content"))
                .andExpect(status().isOk())
                .andExpect(content().string("<rdf:RDF/>"));

        verify(service).getLatestContent();
    }

    private OntologyVersionDetails details() {
        return new OntologyVersionDetails(
                3L,
                3,
                LocalDateTime.of(2026, 9, 2, 20, 0),
                1L,
                "admin",
                "Updated ontology",
                "a".repeat(64),
                "EthnoWear.owx",
                "urn:ethnowear#",
                true,
                null,
                2L,
                2L,
                null,
                null,
                OntologyVersionStatus.ACTIVE
        );
    }
}
