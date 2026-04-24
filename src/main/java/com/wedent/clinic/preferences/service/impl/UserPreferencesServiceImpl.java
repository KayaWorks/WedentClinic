package com.wedent.clinic.preferences.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.preferences.dto.UserPreferencesResponse;
import com.wedent.clinic.preferences.dto.UserPreferencesUpdateRequest;
import com.wedent.clinic.preferences.entity.UserPreferences;
import com.wedent.clinic.preferences.repository.UserPreferencesRepository;
import com.wedent.clinic.preferences.service.PreferenceDefaults;
import com.wedent.clinic.preferences.service.UserPreferencesService;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lazy-create / partial-update flow: a fresh user starts with an in-memory
 * defaults view (no row), the first PATCH materialises the row. This keeps
 * existing tenants from needing a backfill INSERT when the feature ships.
 *
 * <p>The {@code notifications} map is stored as a JSON string in JSONB.
 * Updates merge key-by-key — sending {@code {"appointment_reminder": false}}
 * doesn't wipe the user's other opt-ins.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserPreferencesServiceImpl implements UserPreferencesService {

    private static final TypeReference<LinkedHashMap<String, Boolean>> MAP_TYPE = new TypeReference<>() {};

    private final UserPreferencesRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse getCurrent() {
        Long userId = SecurityUtils.currentUser().userId();
        return repository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(UserPreferencesServiceImpl::defaultsResponse);
    }

    @Override
    public UserPreferencesResponse updateCurrent(UserPreferencesUpdateRequest request) {
        Long userId = SecurityUtils.currentUser().userId();
        UserPreferences prefs = repository.findByUserId(userId)
                .orElseGet(() -> bootstrap(userId));

        applyUpdate(prefs, request);
        // Save explicitly only when we just bootstrapped — otherwise the
        // dirty-checking on the managed entity flushes at commit.
        if (prefs.getId() == null) {
            repository.save(prefs);
        }
        return toResponse(prefs);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private UserPreferences bootstrap(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserPreferences.builder()
                .user(user)
                .language(PreferenceDefaults.LANGUAGE)
                .timezone(PreferenceDefaults.TIMEZONE)
                .dateFormat(PreferenceDefaults.DATE_FORMAT)
                .timeFormat(PreferenceDefaults.TIME_FORMAT)
                .currency(PreferenceDefaults.CURRENCY)
                .notifyEmail(PreferenceDefaults.NOTIFY_EMAIL)
                .notifySms(PreferenceDefaults.NOTIFY_SMS)
                .notifyInApp(PreferenceDefaults.NOTIFY_IN_APP)
                .notifications(PreferenceDefaults.NOTIFICATIONS_JSON)
                .build();
    }

    private void applyUpdate(UserPreferences prefs, UserPreferencesUpdateRequest request) {
        Optional.ofNullable(request.language())  .ifPresent(prefs::setLanguage);
        Optional.ofNullable(request.timezone())  .ifPresent(prefs::setTimezone);
        Optional.ofNullable(request.dateFormat()).ifPresent(prefs::setDateFormat);
        Optional.ofNullable(request.timeFormat()).ifPresent(prefs::setTimeFormat);
        Optional.ofNullable(request.currency())  .ifPresent(prefs::setCurrency);

        if (request.channels() != null) {
            UserPreferencesUpdateRequest.Channels ch = request.channels();
            if (ch.email() != null) prefs.setNotifyEmail(ch.email());
            if (ch.sms()   != null) prefs.setNotifySms(ch.sms());
            if (ch.inApp() != null) prefs.setNotifyInApp(ch.inApp());
        }

        if (request.notifications() != null && !request.notifications().isEmpty()) {
            // Merge the delta into the stored map so the FE can send only
            // the toggles it changed.
            Map<String, Boolean> merged = readMap(prefs.getNotifications());
            merged.putAll(request.notifications());
            prefs.setNotifications(writeMap(merged));
        }
    }

    private UserPreferencesResponse toResponse(UserPreferences prefs) {
        return new UserPreferencesResponse(
                prefs.getLanguage(),
                prefs.getTimezone(),
                prefs.getDateFormat(),
                prefs.getTimeFormat(),
                prefs.getCurrency(),
                new UserPreferencesResponse.Channels(
                        prefs.isNotifyEmail(), prefs.isNotifySms(), prefs.isNotifyInApp()),
                readMap(prefs.getNotifications())
        );
    }

    private static UserPreferencesResponse defaultsResponse() {
        return new UserPreferencesResponse(
                PreferenceDefaults.LANGUAGE,
                PreferenceDefaults.TIMEZONE,
                PreferenceDefaults.DATE_FORMAT,
                PreferenceDefaults.TIME_FORMAT,
                PreferenceDefaults.CURRENCY,
                new UserPreferencesResponse.Channels(
                        PreferenceDefaults.NOTIFY_EMAIL,
                        PreferenceDefaults.NOTIFY_SMS,
                        PreferenceDefaults.NOTIFY_IN_APP),
                Map.of()
        );
    }

    private LinkedHashMap<String, Boolean> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LinkedHashMap<String, Boolean> map = objectMapper.readValue(json, MAP_TYPE);
            return map != null ? map : new LinkedHashMap<>();
        } catch (JsonProcessingException e) {
            // Should not happen for our own writes, but if the JSONB column
            // is somehow corrupted we surface a clean 500 rather than a
            // stack-trace blast.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to decode stored notification preferences", e);
        }
    }

    private String writeMap(Map<String, Boolean> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to encode notification preferences", e);
        }
    }
}
