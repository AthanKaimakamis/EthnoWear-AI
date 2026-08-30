package fmi.ethnowear.application.dto.document.query.history;

import java.util.List;

public record BoundedHistoryDetails<T>(
        List<T> items,
        boolean hasMore
) {
    public BoundedHistoryDetails {
        items = List.copyOf(items);
    }
}
