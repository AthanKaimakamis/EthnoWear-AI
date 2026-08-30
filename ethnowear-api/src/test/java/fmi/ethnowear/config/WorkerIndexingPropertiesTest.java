package fmi.ethnowear.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerIndexingPropertiesTest {

    @Test
    void acceptsTheConfiguredIndexingContract() {
        assertDoesNotThrow(() -> properties(4096, 1024, "bge-m3"));
    }

    @Test
    void rejectsExpectedDimensionsAboveTheWorkerLimit() {
        assertThrows(
                IllegalStateException.class,
                () -> properties(512, 1024, "bge-m3")
        );
    }

    @Test
    void rejectsIdentifiersOutsideDatabaseColumnLimits() {
        assertThrows(
                IllegalStateException.class,
                () -> new WorkerIndexingProperties(
                        10_000,
                        4096,
                        101,
                        150,
                        255,
                        "bge-m3",
                        1024,
                        "ethnowear_chunks_bge_m3_v1"
                )
        );
    }

    private WorkerIndexingProperties properties(
            int maximumDimensions,
            int expectedDimensions,
            String expectedModel
    ) {
        return new WorkerIndexingProperties(
                10_000,
                maximumDimensions,
                100,
                150,
                255,
                expectedModel,
                expectedDimensions,
                "ethnowear_chunks_bge_m3_v1"
        );
    }
}
