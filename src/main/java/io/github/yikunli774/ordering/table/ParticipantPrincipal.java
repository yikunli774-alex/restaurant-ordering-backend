package io.github.yikunli774.ordering.table;

/** The authenticated identity of an anonymous customer: which participant, which session. */
public record ParticipantPrincipal(long participantId, long sessionId) {
}
