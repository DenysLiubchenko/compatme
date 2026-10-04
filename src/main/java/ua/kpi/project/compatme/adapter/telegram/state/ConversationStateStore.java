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
        document.setOrientation(state.orientation());
        document.setSeekingGenders(new ArrayList<>(state.seekingGenders()));
        document.setCountry(state.country());
        document.setCity(state.city());
        document.setSearchScope(state.searchScope());
        document.setMinPreferredAge(state.minPreferredAge());
        document.setMaxPreferredAge(state.maxPreferredAge());
        document.setPendingCountry(state.pendingCountry());
        document.setPendingCity(state.pendingCity());
        document.setSelfDescription(state.selfDescription());
        document.setPreferenceDescription(state.preferenceDescription());
        document.setOptionalFields(state.optionalFields());
        document.setReturnToReview(state.isReturnToReview());
        document.setPhotoUrns(new ArrayList<>(state.photoUrns()));
        return document;
    }

    private ConversationState toState(ConversationStateDocument document) {
        ConversationState state = new ConversationState(document.getTelegramUserId());
        state.setStep(ConversationStep.valueOf(document.getStep()));
        state.setName(document.getName());
        state.setAge(document.getAge());
        state.setGender(document.getGender());
        state.setOrientation(document.getOrientation());
        if (document.getSeekingGenders() != null) {
            state.seekingGenders().addAll(new LinkedHashSet<>(document.getSeekingGenders()));
        }
        state.setCountry(document.getCountry());
        state.setCity(document.getCity());
        state.setSearchScope(document.getSearchScope());
        state.setPreferredAgeRange(document.getMinPreferredAge(), document.getMaxPreferredAge());
        state.setPendingCountry(document.getPendingCountry());
        state.setPendingCity(document.getPendingCity());
        state.setSelfDescription(document.getSelfDescription());
        state.setPreferenceDescription(document.getPreferenceDescription());
        state.setOptionalFields(document.getOptionalFields());
        state.setReturnToReview(document.isReturnToReview());
        if (document.getPhotoUrns() != null) {
            state.photoUrns().addAll(document.getPhotoUrns());
        }
        return state;
    }
}
