package fmi.ethnowear.application.dto.conversation;

import java.util.List;

public record ConversationArchiveFiltersDetails(
        List<String> categoryLocalNames,
        List<String> entityLocalNames,
        List<String> regionLocalNames
) {

    public ConversationArchiveFiltersDetails {
        categoryLocalNames = List.copyOf(categoryLocalNames);
        entityLocalNames = List.copyOf(entityLocalNames);
        regionLocalNames = List.copyOf(regionLocalNames);
    }
}
