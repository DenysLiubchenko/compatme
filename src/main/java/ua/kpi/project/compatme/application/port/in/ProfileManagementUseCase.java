package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/**
 * Inbound use case for basic profile/user CRUD. The single combined User/Profile entity choice
 * (per project scope) means this covers both "account" and "dating profile" concerns.
 *
 * <p>Implemented by {@code application.service} and driven exclusively by
 * {@code adapter.in.web} REST controllers.
 */
public interface ProfileManagementUseCase {

    Profile createOrUpdateProfile(CreateOrUpdateProfileCommand command);

    Profile getProfile(ProfileId id);

    Profile getByTelegramUserId(String telegramUserId);

    List<Profile> listProfiles();

    void deleteProfile(ProfileId id);
}
