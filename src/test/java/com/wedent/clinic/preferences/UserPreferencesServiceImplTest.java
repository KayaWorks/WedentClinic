package com.wedent.clinic.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.preferences.dto.UserPreferencesResponse;
import com.wedent.clinic.preferences.dto.UserPreferencesUpdateRequest;
import com.wedent.clinic.preferences.entity.UserPreferences;
import com.wedent.clinic.preferences.repository.UserPreferencesRepository;
import com.wedent.clinic.preferences.service.PreferenceDefaults;
import com.wedent.clinic.preferences.service.impl.UserPreferencesServiceImpl;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPreferencesServiceImplTest {

    private final UserPreferencesRepository repository = Mockito.mock(UserPreferencesRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserPreferencesServiceImpl service = new UserPreferencesServiceImpl(
            repository, userRepository, objectMapper);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── read ───────────────────────────────────────────────────────────────

    @Test
    void getCurrent_noRow_returnsDefaultsResponse() {
        authenticate(7L);
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());

        UserPreferencesResponse response = service.getCurrent();

        assertThat(response.language()).isEqualTo(PreferenceDefaults.LANGUAGE);
        assertThat(response.timezone()).isEqualTo(PreferenceDefaults.TIMEZONE);
        assertThat(response.dateFormat()).isEqualTo(PreferenceDefaults.DATE_FORMAT);
        assertThat(response.timeFormat()).isEqualTo(PreferenceDefaults.TIME_FORMAT);
        assertThat(response.currency()).isEqualTo(PreferenceDefaults.CURRENCY);
        assertThat(response.channels().email()).isEqualTo(PreferenceDefaults.NOTIFY_EMAIL);
        assertThat(response.channels().sms()).isEqualTo(PreferenceDefaults.NOTIFY_SMS);
        assertThat(response.channels().inApp()).isEqualTo(PreferenceDefaults.NOTIFY_IN_APP);
        assertThat(response.notifications()).isEmpty();
    }

    @Test
    void getCurrent_existingRow_decodesNotificationsJson() {
        authenticate(7L);
        UserPreferences prefs = builderWithDefaults()
                .notifications("{\"appointment_reminder\":true,\"login_alert\":false}")
                .build();
        prefs.setId(99L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(prefs));

        UserPreferencesResponse response = service.getCurrent();

        assertThat(response.notifications())
                .containsEntry("appointment_reminder", true)
                .containsEntry("login_alert", false);
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void updateCurrent_noRow_bootstrapsFromUserAndSaves() {
        authenticate(7L);
        User user = User.builder().email("doc@example.com").build();
        user.setId(7L);
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserPreferencesResponse response = service.updateCurrent(new UserPreferencesUpdateRequest(
                "en-US", null, null, null, null, null, null));

        ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
        verify(repository).save(captor.capture());
        UserPreferences saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getLanguage()).isEqualTo("en-US");
        assertThat(saved.getTimezone()).isEqualTo(PreferenceDefaults.TIMEZONE);
        assertThat(saved.getNotifications()).isEqualTo(PreferenceDefaults.NOTIFICATIONS_JSON);
        assertThat(response.language()).isEqualTo("en-US");
    }

    @Test
    void updateCurrent_noRow_missingUser_throwsResourceNotFound() {
        authenticate(7L);
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrent(
                new UserPreferencesUpdateRequest("tr", null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateCurrent_existingRow_dirtyChecksWithoutExplicitSave() {
        authenticate(7L);
        UserPreferences prefs = builderWithDefaults().build();
        prefs.setId(99L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(prefs));

        service.updateCurrent(new UserPreferencesUpdateRequest(
                null, "Europe/Berlin", "YYYY-MM-DD", null, "EUR", null, null));

        // Existing row → relies on JPA dirty checking, no explicit save call.
        verify(repository, never()).save(any());
        assertThat(prefs.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(prefs.getDateFormat()).isEqualTo("YYYY-MM-DD");
        assertThat(prefs.getCurrency()).isEqualTo("EUR");
        // Untouched fields stay at defaults.
        assertThat(prefs.getLanguage()).isEqualTo(PreferenceDefaults.LANGUAGE);
        assertThat(prefs.getTimeFormat()).isEqualTo(PreferenceDefaults.TIME_FORMAT);
    }

    @Test
    void updateCurrent_channels_appliedIndependently_nullsSkip() {
        authenticate(7L);
        UserPreferences prefs = builderWithDefaults().build();
        prefs.setId(99L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(prefs));

        // Toggle SMS only — email + inApp passed as null and must stay
        // at their pre-existing values.
        service.updateCurrent(new UserPreferencesUpdateRequest(
                null, null, null, null, null,
                new UserPreferencesUpdateRequest.Channels(null, true, null),
                null));

        assertThat(prefs.isNotifyEmail()).isEqualTo(PreferenceDefaults.NOTIFY_EMAIL);
        assertThat(prefs.isNotifySms()).isTrue();
        assertThat(prefs.isNotifyInApp()).isEqualTo(PreferenceDefaults.NOTIFY_IN_APP);
    }

    @Test
    void updateCurrent_notifications_mergeIsDeltaNotReplace() {
        authenticate(7L);
        UserPreferences prefs = builderWithDefaults()
                .notifications("{\"appointment_reminder\":true,\"login_alert\":true}")
                .build();
        prefs.setId(99L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(prefs));

        service.updateCurrent(new UserPreferencesUpdateRequest(
                null, null, null, null, null, null,
                Map.of("login_alert", false, "marketing", true)));

        UserPreferencesResponse response = service.updateCurrent(new UserPreferencesUpdateRequest(
                null, null, null, null, null, null, null));

        // login_alert overwritten, marketing added, appointment_reminder
        // untouched (still true).
        assertThat(response.notifications())
                .containsEntry("appointment_reminder", true)
                .containsEntry("login_alert", false)
                .containsEntry("marketing", true);
    }

    @Test
    void updateCurrent_emptyNotificationsMap_skipsMergeEntirely() {
        authenticate(7L);
        UserPreferences prefs = builderWithDefaults()
                .notifications("{\"appointment_reminder\":true}")
                .build();
        prefs.setId(99L);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(prefs));

        service.updateCurrent(new UserPreferencesUpdateRequest(
                null, null, null, null, null, null, Map.of()));

        // Empty map must NOT wipe the stored opt-ins.
        assertThat(prefs.getNotifications()).isEqualTo("{\"appointment_reminder\":true}");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static UserPreferences.UserPreferencesBuilder builderWithDefaults() {
        return UserPreferences.builder()
                .language(PreferenceDefaults.LANGUAGE)
                .timezone(PreferenceDefaults.TIMEZONE)
                .dateFormat(PreferenceDefaults.DATE_FORMAT)
                .timeFormat(PreferenceDefaults.TIME_FORMAT)
                .currency(PreferenceDefaults.CURRENCY)
                .notifyEmail(PreferenceDefaults.NOTIFY_EMAIL)
                .notifySms(PreferenceDefaults.NOTIFY_SMS)
                .notifyInApp(PreferenceDefaults.NOTIFY_IN_APP)
                .notifications(PreferenceDefaults.NOTIFICATIONS_JSON);
    }

    private static void authenticate(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", 100L, null, Set.of("DOCTOR"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
