package ua.kpi.project.compatme.adapter.telegram.state;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Loads/saves/clears {@link ConversationState} for a Telegram user, backed by MongoDB via
 * {@link SpringDataConversationStateRepository}. The only place {@link ConversationStateDocument}
 * is visible — {@link ua.kpi.project.compatme.adapter.telegram.ConversationFlowHandler} only ever
 * works with the plain {@link ConversationState}.
 */
@Component
public class ConversationStateStore {

    private final SpringDataConversationStateRepository repository;

    public ConversationStateStore(SpringDataConversationStateRepository repository) {
        this.repository = repository;
    }

    /** Returns the stored state for {@code telegramUserId}, or a fresh {@code WELCOME}-step state if none exists. */
    public ConversationState loadOrCreate(String telegramUserId) {
        return repository.findById(telegramUserId)
                .map(this::toState)
                .orElseGet(() -> new ConversationState(telegramUserId));
    }

    public Optional<ConversationState> load(String telegramUserId) {
        return repository.findById(telegramUserId).map(this::toState);
    }

    public void save(ConversationState state) {
        repository.save(toDocument(state));
    }

    /** Removes the stored state entirely, so a stray leftover message can't be misrouted into a finished/deleted flow. */
    public void clear(String telegramUserId) {
        repository.deleteById(telegramUserId);
    }

    private ConversationStateDocument toDocument(ConversationState state) {
        ConversationStateDocument document = new ConversationStateDocument();
        document.setTelegramUserId(state.telegramUserId());
        document.setStep(state.step().name());
        document.setName(state.name());
        document.setAge(state.age());
        document.setGender(state.gender());
        document.setSeekingGenders(new ArrayList<>(state.seekingGenders()));
        document.setCountry(state.country());
        document.setCity(state.city());
        document.setPendingCountry(state.pendingCountry());
        document.setPendingCity(state.pendingCity());
        document.setSelfDescription(state.selfDescription());
        document.setPreferenceDescription(state.preferenceDescription());
        document.setReturnToReview(state.isReturnToReview());
        document.setPhotoFileIds(new ArrayList<>(state.photoFileIds()));
        return document;
    }

    private ConversationState toState(ConversationStateDocument document) {
        ConversationState state = new ConversationState(document.getTelegramUserId());
        state.setStep(ConversationStep.valueOf(document.getStep()));
        state.setName(document.getName());
        state.setAge(document.getAge());
        state.setGender(document.getGender());
        if (document.getSeekingGenders() != null) {
            state.seekingGenders().addAll(new LinkedHashSet<>(document.getSeekingGenders()));
        }
        state.setCountry(document.getCountry());
        state.setCity(document.getCity());
        state.setPendingCountry(document.getPendingCountry());
        state.setPendingCity(document.getPendingCity());
        state.setSelfDescription(document.getSelfDescription());
        state.setPreferenceDescription(document.getPreferenceDescription());
        state.setReturnToReview(document.isReturnToReview());
        if (document.getPhotoFileIds() != null) {
            state.photoFileIds().addAll(document.getPhotoFileIds());
        }
        return state;
    }
}
