package fmi.ethnowear.domain.model.conversation;

public enum ConversationProgressStage {
    RECEIVED,
    RESOLVING_ENTITIES,
    READING_ONTOLOGY,
    SEARCHING_ARCHIVE,
    RETRIEVING_SOURCES,
    REASONING,
    INTERPRETING,
    GENERATING_ANSWER,
    VALIDATING_ANSWER
}