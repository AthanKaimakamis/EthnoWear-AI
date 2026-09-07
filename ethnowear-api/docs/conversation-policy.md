# Conversation policy

The public conversation API now separates social interaction from source-grounded
knowledge. No database or frontend response-schema migration is required.

- Exact Bulgarian/English greetings, thanks, wellbeing questions and basic help
  receive maintained replies without ontology, RAG, JADE or generation calls.
- Other messages use a bounded Ollama intent request. Only KNOWLEDGE, HELP,
  CLARIFY and OUT_OF_SCOPE are accepted. Model-generated answer text and extra
  fields are rejected. Unparseable decisions produce a clarification; transport
  failures retain the existing Ollama-unavailable error path.
- Mixed social/factual messages must be classified. Merely mentioning EthnoWear
  does not make an unrelated request in scope. This classifier is advisory, not
  a guaranteed security boundary.
- Only KNOWLEDGE enters the existing evidence/reasoning/generation workflow.
  Explicit pasted-source approval claims combined with evidence IDs or requests
  to assert a verified fact receive a controlled provenance-boundary reply with
  no citations. These messages cannot anchor later simple follow-ups. This is a
  targeted guard, not a complete detector of forged sources or prompt injection.
  Zero evidence bypasses generation. Responses with no supported claims use a
  controlled fallback; an insufficientEvidence flag cannot authorize free prose.
- Claim and citation validation remains enabled. Supported claims are displayed
  in short paragraphs; a partial-answer limitation is application-authored.
  Generation requests include a JSON schema for the answer/claims contract;
  schema compliance does not replace grounding or citation validation.
- Bounded history retains prior source IDs and entity local names. Follow-ups
  re-retrieve current approved evidence; previous prose is not factual evidence.
  Simple follow-up matching skips social interjections and stops at a new
  unrelated substantive subject. Complex references may still need clarification.
- Frontend availability checks continue to apply. This does not enable offline
  social chat while required dependencies are marked unavailable.

The frontend groups passage citations by non-null authoritative source ID: one
book entry retains all original reference numbers. It does not merge distinct
source IDs just because titles match, and unknown provenance is grouped only by
the existing citation ID. API passage citations and stored answers are unchanged.

The conversational replies are deliberately constrained templates, not unrestricted
small talk. HELP currently explains capabilities and archive navigation generally;
it is not a complete application-help knowledge base. Classification and lexical
claim validation need ongoing Bulgarian/English evaluation; neither proves semantic
entailment or immunity to prompt injection.

Run focused regressions with Java 21:

    mvn -f ethnowear-api/pom.xml '-Dtest=*Conversation*Test' test

Manual cases: greetings; greeting plus motif question; rocket-science request;
an unrelated question containing EthnoWear; source-boundary bypass; supported
motif question followed by thanks and a simplification; topic switch followed by
"why?"; insufficient evidence; Ollama/RAG outage.
