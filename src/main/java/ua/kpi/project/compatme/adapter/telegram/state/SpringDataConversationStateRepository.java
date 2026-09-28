package ua.kpi.project.compatme.adapter.telegram.state;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for conversation state — pure infrastructure plumbing, used
 * only internally by {@link ConversationStateStore}.
 */
public interface SpringDataConversationStateRepository extends MongoRepository<ConversationStateDocument, String> {
}
