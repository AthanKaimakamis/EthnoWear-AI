package fmi.ethnowear.application.service.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import fmi.ethnowear.infrastructure.ontology.jena.embroidery.EmbroideryOntology;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BulgarianEvidenceCasesTest {

    private static JsonNode dataset;
    private static JsonNode liveRetrieval;
    private static JenaOntologyStore store;
    private static EmbroideryOntology ontology;

    @BeforeAll
    static void loadEvidence() throws Exception {
        try (var input = BulgarianEvidenceCasesTest.class.getResourceAsStream(
                "/retrieval/bg-ontology-document-cases-v1.json"
        )) {
            assertNotNull(input);
            dataset = new ObjectMapper().readTree(input);
        }

        try (var input = BulgarianEvidenceCasesTest.class.getResourceAsStream(
                "/" + dataset.get("retrievalRun").get("snapshotFile").asText()
        )) {
            assertNotNull(input);
            liveRetrieval = new ObjectMapper().readTree(input);
        }

        store = new JenaOntologyStore(
                Path.of(dataset.get("ontologyFile").asText()),
                dataset.get("namespace").asText()
        );
        ontology = new EmbroideryOntology(store);
    }

    @TestFactory
    Stream<DynamicTest> ontologyExpectationsMatchTheAuditedCheckout() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode evaluationCase : dataset.get("cases")) {
            tests.add(DynamicTest.dynamicTest(evaluationCase.get("id").asText(), () -> {
                for (JsonNode check : evaluationCase.get("ontologyChecks")) {
                    Set<String> expected = strings(check.get("expectedLocalNames"));
                    Set<String> actual = new HashSet<>(query(
                            check.get("operation").asText(),
                            check.get("localName").asText()
                    ).stream().map(OntologyResource::localName).toList());
                    assertEquals(expected, actual, check.toString());
                }
                for (String name : strings(evaluationCase.get("expectedConcepts"))) {
                    boolean exists = store.read(model -> model.containsResource(
                            model.getResource(store.uri(name))
                    ));
                    assertTrue(exists, "Unknown expected ontology identity: " + name);
                }
            }));
        }
        return tests.stream();
    }

    @Test
    void auditedEvidenceAndUnresolvedImageGroundTruthRemainDistinct() {
        assertEquals(7, dataset.get("cases").size());
        assertEquals("LIVE_RETRIEVAL_AND_SQL_CITATIONS_VERIFIED", dataset.get("liveEvidenceStatus").asText());
        Set<String> ids = new HashSet<>();
        Set<String> allowedStatuses = Set.of("ANSWERABLE", "PARTIALLY_ANSWERABLE", "UNSUPPORTED");

        for (JsonNode evaluationCase : dataset.get("cases")) {
            assertTrue(ids.add(evaluationCase.get("id").asText()));
            assertFalse(evaluationCase.get("question").asText().isBlank());
            assertFalse(evaluationCase.get("variants").isEmpty());
            assertTrue(allowedStatuses.contains(evaluationCase.get("verifiedScopeStatus").asText()));
            assertTrue(evaluationCase.has("documentGroundTruth"));
            assertTrue(evaluationCase.has("archiveGroundTruth"));
            if (evaluationCase.get("intent").asText().equals("IMAGE_REGIONAL_ATTRIBUTION")) {
                assertTrue(evaluationCase.get("documentGroundTruth").isNull());
                assertTrue(evaluationCase.get("archiveGroundTruth").isNull());
                assertTrue(evaluationCase.get("imageGroundTruth").isNull());
            } else {
                JsonNode documents = evaluationCase.get("documentGroundTruth");
                assertEquals("AUDITED_CURRENT_CORPUS", documents.get("status").asText());
                assertTrue(documents.get("sufficientChunkIds").isArray());
                assertTrue(documents.get("contextOnlyChunkIds").isArray());
                assertEquals("AUDITED_ALL_PUBLISHED_ITEMS",
                        evaluationCase.get("archiveGroundTruth").get("status").asText());
                assertTrue(evaluationCase.get("archiveGroundTruth").get("matchingItemIds").isArray());
            }
            assertFalse(evaluationCase.get("requiredBehavior").isEmpty());
            assertFalse(evaluationCase.get("forbiddenClaims").isEmpty());
        }
    }

    @Test
    void contextOnlyEvidenceHasExactCitationButDoesNotProveACompleteAnswer() {
        JsonNode composition = dataset.get("cases").get(0);
        JsonNode citation = composition.get("verifiedEvidence").get(0);
        assertEquals(10262, citation.get("chunkId").asLong());
        assertEquals(40011, citation.get("documentId").asLong());
        assertEquals(40252, citation.get("pageId").asLong());
        assertEquals(3, citation.get("pageSequence").asInt());
        assertEquals(2, citation.get("pdfPageIndex").asInt());
        assertEquals("113", citation.get("printedPageNumber").asText());
        assertEquals(2, citation.get("sourceId").asLong());
        assertEquals(3, citation.get("sourceReferenceId").asLong());
        Set<String> current = strings(dataset.get("liveSnapshot").get("currentChunkIds"));
        for (JsonNode evaluationCase : dataset.get("cases")) {
            JsonNode documents = evaluationCase.get("documentGroundTruth");
            if (documents.isNull())
                continue;
            Set<String> sufficient = strings(documents.get("sufficientChunkIds"));
            Set<String> contextOnly = strings(documents.get("contextOnlyChunkIds"));
            assertTrue(current.containsAll(sufficient));
            assertTrue(current.containsAll(contextOnly));
            assertTrue(sufficient.stream().noneMatch(contextOnly::contains));
        }
        assertTrue(composition.get("documentGroundTruth").get("sufficientChunkIds").isEmpty());
    }

    @Test
    void retrievalAvailabilityIsSeparateFromAnswerSupportAndRights() {
        JsonNode run = dataset.get("retrievalRun");
        int requests = 0;
        for (JsonNode evaluationCase : dataset.get("cases")) {
            if (!evaluationCase.get("intent").asText().equals("IMAGE_REGIONAL_ATTRIBUTION"))
                requests += 1 + evaluationCase.get("variants").size();
        }
        assertEquals(requests, run.get("textRequests").asInt());
        assertEquals(0, run.get("unavailableRequests").asInt());
        assertEquals(requests, run.get("successfulRequests").asInt());
        assertEquals(0.0, run.get("precisionAt5").asDouble());
        assertTrue(run.get("recallAt5").isNull());
        assertFalse(run.get("imageCaseRun").asBoolean());
        for (JsonNode figure : dataset.get("liveSnapshot").get("elhovoApprovedFigures")) {
            assertEquals("APPROVED", figure.get("reviewState").asText());
            assertEquals("UNKNOWN", figure.get("publicDisplayPermission").asText());
        }
        assertTrue(dataset.get("cases").get(3).get("mediaGroundTruth")
                .get("rightsClearedMediaIds").isEmpty());
    }

    @TestFactory
    Stream<DynamicTest> recordedLiveRankingsUseCurrentCitedChunks() {
        List<DynamicTest> tests = new ArrayList<>();
        Set<String> current = strings(dataset.get("liveSnapshot").get("currentChunkIds"));
        Set<String> cited = new HashSet<>();
        liveRetrieval.get("citations").forEach(citation -> cited.add(citation.get("chunkId").asText()));

        for (JsonNode run : liveRetrieval.get("runs")) {
            tests.add(DynamicTest.dynamicTest(run.get("question").asText(), () -> {
                assertEquals(200, run.get("httpStatus").asInt());
                JsonNode ranked = run.get("rankedChunkIds");
                assertEquals(5, ranked.size());
                assertEquals(5, strings(ranked).size());
                assertTrue(current.containsAll(strings(ranked)));
                assertTrue(cited.containsAll(strings(ranked)));
                assertEquals(ranked.size(), run.get("similarities").size());
                double previous = Double.POSITIVE_INFINITY;
                for (JsonNode score : run.get("similarities")) {
                    assertTrue(Double.isFinite(score.asDouble()));
                    assertTrue(score.asDouble() <= previous);
                    previous = score.asDouble();
                }
            }));
        }
        return tests.stream();
    }

    @Test
    void recordedMetricsDistinguishContextMatchesFromPositiveControls() {
        int contextRequests = 0;
        int contextHits = 0;
        int controlRequests = 0;
        int controlHits = 0;
        double reciprocalRanks = 0;
        Set<String> observedQuestions = new HashSet<>();
        for (JsonNode run : liveRetrieval.get("runs")) {
            assertTrue(observedQuestions.add(run.get("question").asText()));
            List<Long> ranked = new ArrayList<>();
            run.get("rankedChunkIds").forEach(id -> ranked.add(id.asLong()));
            if (Set.of("BG-01", "BG-02").contains(run.get("caseId").asText())) {
                contextRequests++;
                if (ranked.contains(10262L))
                    contextHits++;
            }
            if (!run.get("expectedChunkId").isNull()) {
                controlRequests++;
                int index = ranked.indexOf(run.get("expectedChunkId").asLong());
                if (index >= 0) {
                    controlHits++;
                    reciprocalRanks += 1.0 / (index + 1);
                }
            }
        }
        for (JsonNode evaluationCase : dataset.get("cases")) {
            if (evaluationCase.get("id").asText().equals("BG-07"))
                continue;
            assertTrue(observedQuestions.contains(evaluationCase.get("question").asText()));
            evaluationCase.get("variants").forEach(variant ->
                    assertTrue(observedQuestions.contains(variant.asText())));
        }
        JsonNode run = dataset.get("retrievalRun");
        assertEquals(contextRequests, run.get("contextOnlyHitAt5").get("requests").asInt());
        assertEquals(contextHits, run.get("contextOnlyHitAt5").get("hits").asInt());
        JsonNode controls = run.get("positiveControls");
        assertEquals(controlRequests, controls.get("requests").asInt());
        assertEquals(controlHits, controls.get("hitsAt5").asInt());
        assertEquals(reciprocalRanks / controlRequests,
                controls.get("meanReciprocalRankAt5").asDouble(), 0.000001);
        assertEquals(liveRetrieval.get("runs").size(),
                liveRetrieval.get("verification").get("successfulRequests").asInt());
    }

    @Test
    void similarityThresholdIsDerivedFromVerifiedPositiveControls() {
        double minimumGoldScore = Double.POSITIVE_INFINITY;
        int controls = 0;

        for (JsonNode run : liveRetrieval.get("runs")) {
            if (run.get("expectedChunkId").isNull())
                continue;

            controls++;
            long expectedChunkId = run.get("expectedChunkId").asLong();
            int expectedIndex = -1;
            for (int index = 0; index < run.get("rankedChunkIds").size(); index++) {
                if (run.get("rankedChunkIds").get(index).asLong() == expectedChunkId) {
                    expectedIndex = index;
                    break;
                }
            }

            assertTrue(expectedIndex >= 0);
            minimumGoldScore = Math.min(
                    minimumGoldScore,
                    run.get("similarities").get(expectedIndex).asDouble()
            );
        }

        JsonNode calibration = dataset.get("similarityThresholdCalibration");
        assertEquals(controls, calibration.get("positiveControlCount").asInt());
        assertEquals(minimumGoldScore, calibration.get("minimumVerifiedGoldSimilarity").asDouble(), 0.0000001);
        assertEquals(Math.floor(minimumGoldScore * 100.0) / 100.0,
                calibration.get("initialMinimumSimilarity").asDouble(), 0.0000001);
    }

    @Test
    void recordsActualAliasResolutionRatherThanPretendingTypoSupportExists() {
        for (JsonNode observation : dataset.get("regionResolutionObservations")) {
            JsonNode expected = observation.get("observedLocalName");
            String actual = ontology.findRegionByName(
                    observation.get("input").asText(),
                    OntologyLanguage.fromTag("bg")
            ).map(OntologyResource::localName).orElse(null);

            assertEquals(expected.isNull() ? null : expected.asText(), actual);
        }
    }

    @Test
    void stitchLabelsDoNotProvideMissingDefinitionsOrInstructions() {
        List<String> names = new ArrayList<>(List.of("ChainTechnique", "CrossTechnique"));
        ontology.listTechniquesOfType("ChainTechnique").forEach(x -> names.add(x.localName()));
        ontology.listTechniquesOfType("CrossTechnique").forEach(x -> names.add(x.localName()));

        for (String name : names) {
            boolean hasComment = store.read(model -> model.contains(
                    model.getResource(store.uri(name)), RDFS.comment
            ));
            assertFalse(hasComment, "A new definition requires reevaluating this baseline: " + name);
        }
    }

    private static Set<String> strings(JsonNode values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static List<OntologyResource> query(String operation, String name) {
        return switch (operation) {
            case "regionOrnaments" -> ontology.listOrnamentsUsedByRegion(name);
            case "regionTechniques" -> ontology.listTechniquesUsedByRegion(name);
            case "styleRegion" -> ontology.findRegionForRegionalEmbroidery(name).stream().toList();
            case "styleOrnaments" -> ontology.listOrnamentsOfEmbroidery(name);
            case "styleTechniques" -> ontology.listTechniquesOfEmbroidery(name);
            case "styleColors" -> ontology.listColorsOfEmbroidery(name);
            case "styleMotifs" -> ontology.listMotifsOfEmbroidery(name);
            case "techniqueMembers" -> ontology.listTechniquesOfType(name);
            default -> throw new IllegalArgumentException("Unknown evaluation operation: " + operation);
        };
    }
}
