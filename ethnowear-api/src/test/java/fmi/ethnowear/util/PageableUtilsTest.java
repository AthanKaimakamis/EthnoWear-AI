package fmi.ethnowear.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

class PageableUtilsTest {

    @Test
    void boundedPreservesSorting() {
        Pageable input = PageRequest.of(
                2,
                25,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        assertEquals(input, PageableUtils.bounded(input, 100, "Document"));
    }

    @Test
    void boundedUnsortedRemovesClientSorting() {
        Pageable result = PageableUtils.boundedUnsorted(
                PageRequest.of(1, 20, Sort.by("createdAt")),
                100,
                "History"
        );

        assertTrue(result.getSort().isUnsorted());
        assertEquals(1, result.getPageNumber());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void rejectsMissingAndOversizedPaging() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PageableUtils.bounded(Pageable.unpaged(), 100, "Document")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PageableUtils.bounded(PageRequest.of(0, 101), 100, "Document")
        );
    }
}
