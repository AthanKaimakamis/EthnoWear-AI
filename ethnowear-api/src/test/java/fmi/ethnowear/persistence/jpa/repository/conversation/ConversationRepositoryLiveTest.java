package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationGuestSession;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvidence;
import fmi.ethnowear.domain.model.conversation.ConversationEvidenceType;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUser;
import fmi.ethnowear.util.ContentHashUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
class ConversationRepositoryLiveTest {

    @Autowired private ConversationRepository conversations;
    @Autowired private ConversationGuestSessionRepository sessions;
    @Autowired private ConversationTurnEventRepository events;
    @Autowired private ConversationTurnEvidenceRepository evidence;
    @Autowired private ConversationTurnRepository turns;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private ConversationGuestSession guest() {
        return sessions.saveAndFlush(new ConversationGuestSession(
                ContentHashUtils.sha256(UUID.randomUUID().toString()),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
        ));
    }

    private ConversationTurn turn() {
        var conversation = conversations.saveAndFlush(new Conversation(null, guest(), UUID.randomUUID(), "bg"));
        var turn = new ConversationTurn(conversation, UUID.randomUUID(), 1, "Question");
        entityManager.persist(turn);
        entityManager.flush();
        return turn;
    }

    @Test
    void turnQueriesPreserveConversationScopeOrderingAndRequestIdentity() {
        var first = turn();
        long conversationId = first.getConversation().getId();
        first.cancel(first.getCreatedAt());
        turns.flush();
        var second = turns.saveAndFlush(new ConversationTurn(first.getConversation(), UUID.randomUUID(), 2, "Follow up"));
        var unrelated = turn();
        entityManager.clear();

        assertThat(turns.findByConversation_IdAndPublicId(conversationId, first.getPublicId())).isPresent();
        assertThat(turns.findByConversation_IdAndPublicId(conversationId, unrelated.getPublicId())).isEmpty();
        assertThat(turns.findByConversation_IdAndClientRequestId(conversationId, first.getClientRequestId()))
                .get().extracting(ConversationTurn::getId).isEqualTo(first.getId());
        assertThat(turns.findByConversation_IdAndClientRequestId(conversationId, unrelated.getClientRequestId())).isEmpty();
        assertThat(turns.findFirstByConversation_IdOrderByTurnSequenceDesc(conversationId))
                .get().extracting(ConversationTurn::getId).isEqualTo(second.getId());
        var history = turns.findByConversation_IdOrderByTurnSequenceDesc(conversationId, PageRequest.of(0, 1));
        assertThat(history.getTotalElements()).isEqualTo(2);
        assertThat(history.getContent()).extracting(ConversationTurn::getId).containsExactly(second.getId());
        assertThat(turns.existsByConversation_IdAndActiveTrue(conversationId)).isTrue();
        var current = turns.findById(second.getId()).orElseThrow();
        current.cancel(current.getCreatedAt());
        turns.flush();
        assertThat(turns.existsByConversation_IdAndActiveTrue(conversationId)).isFalse();
    }

    @Test
    void lockingQueriesUsePessimisticWritesAndRespectTurnConversation() {
        var turn = turn();
        long conversationId = turn.getConversation().getId();
        entityManager.clear();

        var conversation = conversations.findForUpdate(conversationId).orElseThrow();
        assertThat(entityManager.getLockMode(conversation)).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var locked = turns.findForUpdate(conversationId, turn.getPublicId()).orElseThrow();
        assertThat(entityManager.getLockMode(locked)).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThat(turns.findForUpdate(-1, turn.getPublicId())).isEmpty();
        assertThat(turns.findForUpdate(conversationId, UUID.randomUUID())).isEmpty();
        assertThat(conversations.findForUpdate(-1)).isEmpty();
    }

    @Test
    void eventReplayIsBoundedOrderedAndScopedToTheTurn() {
        var turn = turn();
        events.saveAndFlush(new ConversationTurnEvent(turn));
        turn.start(turn.getCreatedAt());
        events.saveAndFlush(new ConversationTurnEvent(turn));
        turn.advance(ConversationProgressStage.REASONING);
        events.saveAndFlush(new ConversationTurnEvent(turn));
        events.saveAndFlush(new ConversationTurnEvent(turn()));
        entityManager.clear();

        var first = events.findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(turn.getId(), 0, PageRequest.of(0, 2));
        assertThat(first).extracting(ConversationTurnEvent::getEventId).containsExactly(1L, 2L);
        assertThat(first.getFirst().getStatus()).isEqualTo(ConversationTurnStatus.QUEUED);
        assertThat(first.getLast().getStatus()).isEqualTo(ConversationTurnStatus.RUNNING);
        var next = events.findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(turn.getId(), 2, PageRequest.of(0, 2));
        assertThat(next).extracting(ConversationTurnEvent::getEventId).containsExactly(3L);
        assertThat(next.getFirst().getStage()).isEqualTo(ConversationProgressStage.REASONING);
        assertThat(events.findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(turn.getId(), 3, PageRequest.of(0, 2))).isEmpty();
        assertThat(entityManager.find(ConversationTurn.class, turn.getId()).getLastEventId()).isEqualTo(3);
    }

    @Test
    void evidenceQueriesPreservePrivateSnapshotsAndTurnScope() {
        var firstTurn = turn();
        var secondTurn = turn();
        String snapshot = "{\"documentId\":1,\"pageIds\":[2],\"printedPageNumber\":\"111\"}";
        var first = evidence.saveAndFlush(new ConversationTurnEvidence(firstTurn, "document:1", ConversationEvidenceType.DOCUMENT, snapshot));
        var second = evidence.saveAndFlush(new ConversationTurnEvidence(firstTurn, "ontology:1", ConversationEvidenceType.ONTOLOGY, "{}"));
        evidence.saveAndFlush(new ConversationTurnEvidence(secondTurn, "document:1", ConversationEvidenceType.DOCUMENT, "{}"));
        entityManager.clear();

        assertThat(evidence.findByTurn_IdOrderByIdAsc(firstTurn.getId(), PageRequest.of(0, 1)))
                .extracting(ConversationTurnEvidence::getId).containsExactly(first.getId());
        assertThat(evidence.findByTurn_IdOrderByIdAsc(firstTurn.getId(), PageRequest.of(1, 1)))
                .extracting(ConversationTurnEvidence::getId).containsExactly(second.getId());
        assertThat(evidence.findByTurn_IdAndEvidenceKey(firstTurn.getId(), "document:1"))
                .get().extracting(ConversationTurnEvidence::getSnapshotJson).isEqualTo(snapshot);
        assertThat(evidence.findByTurn_IdAndEvidenceKey(secondTurn.getId(), "ontology:1")).isEmpty();
    }

    @Test
    void guestOwnershipScopesIdentifiersRequestsAndHistory() {
        var first = guest();
        var second = guest();
        UUID request = UUID.randomUUID();
        var owned = conversations.saveAndFlush(new Conversation(null, first, request, "bg"));
        var other = conversations.saveAndFlush(new Conversation(null, second, request, "bg"));
        entityManager.clear();

        assertThat(conversations.findByPublicIdAndGuestSession_Id(owned.getPublicId(), first.getId())).isPresent();
        assertThat(conversations.findByPublicIdAndGuestSession_Id(owned.getPublicId(), second.getId())).isEmpty();
        assertThat(conversations.findByClientRequestIdAndGuestSession_Id(request, first.getId()))
                .get().extracting(Conversation::getId).isEqualTo(owned.getId());
        assertThat(conversations.findByClientRequestIdAndGuestSession_Id(request, second.getId()))
                .get().extracting(Conversation::getId).isEqualTo(other.getId());
        var page = conversations.findByGuestSession_Id(first.getId(), PageRequest.of(0, 1, Sort.by("id")));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Conversation::getId).containsExactly(owned.getId());
        assertThat(conversations.findByPublicIdAndPublicUser_Id(owned.getPublicId(), first.getId())).isEmpty();
    }

    @Test
    void userOwnershipScopesIdentifiersRequestsAndHistory() {
        var first = new PublicUser("First", "first@example.invalid", LocalDateTime.now(ZoneOffset.UTC));
        var second = new PublicUser("Second", "second@example.invalid", LocalDateTime.now(ZoneOffset.UTC));
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();
        var ids = java.util.List.of(first.getId(), second.getId());
        UUID request = UUID.randomUUID();
        var owned = conversations.saveAndFlush(new Conversation(entityManager.getReference(PublicUser.class, ids.getFirst()), null, request, "bg"));
        var other = conversations.saveAndFlush(new Conversation(entityManager.getReference(PublicUser.class, ids.getLast()), null, request, "en"));
        entityManager.clear();

        assertThat(conversations.findByPublicIdAndPublicUser_Id(owned.getPublicId(), ids.getFirst())).isPresent();
        assertThat(conversations.findByPublicIdAndPublicUser_Id(owned.getPublicId(), ids.getLast())).isEmpty();
        assertThat(conversations.findByClientRequestIdAndPublicUser_Id(request, ids.getFirst()))
                .get().extracting(Conversation::getId).isEqualTo(owned.getId());
        assertThat(conversations.findByClientRequestIdAndPublicUser_Id(request, ids.getLast()))
                .get().extracting(Conversation::getId).isEqualTo(other.getId());
        var page = conversations.findByPublicUser_Id(ids.getFirst(), PageRequest.of(0, 100, Sort.by("id").descending()));
        assertThat(page.getContent()).extracting(Conversation::getId).contains(owned.getId()).doesNotContain(other.getId());
        assertThat(conversations.findByPublicIdAndGuestSession_Id(owned.getPublicId(), ids.getFirst())).isEmpty();
    }

    @Test
    void expiredAndRevokedTokensCannotResolveGuestSession() {
        var session = guest();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = session.getTokenHash();
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, now)).isPresent();
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, session.getExpiresAt())).isEmpty();
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, session.getExpiresAt().plusSeconds(1))).isEmpty();
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter("f".repeat(64), now)).isEmpty();
        session.setRevokedAt(now);
        sessions.flush();
        entityManager.clear();
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, now)).isEmpty();
    }

    @Test
    void sqlGeneratesAndAdvancesVersionsForMutableChatRecords() {
        var session = guest();
        var conversation = conversations.saveAndFlush(new Conversation(null, session, UUID.randomUUID(), "bg"));
        byte[] sessionVersion = session.getRowVersion();
        byte[] conversationVersion = conversation.getRowVersion();
        assertThat(sessionVersion).hasSize(8);
        assertThat(conversationVersion).hasSize(8);
        session.setExpiresAt(session.getExpiresAt().plusDays(1));
        conversation.setTitle("Updated title");
        conversations.flush();
        assertThat(session.getRowVersion()).isNotEqualTo(sessionVersion);
        assertThat(conversation.getRowVersion()).isNotEqualTo(conversationVersion);
        entityManager.clear();
        assertThat(conversations.findByPublicIdAndGuestSession_Id(conversation.getPublicId(), session.getId()))
                .get().extracting(Conversation::getTitle).isEqualTo("Updated title");
    }
}
