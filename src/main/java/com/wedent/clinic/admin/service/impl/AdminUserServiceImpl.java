package com.wedent.clinic.admin.service.impl;

import com.wedent.clinic.admin.dto.AdminResetPasswordResponse;
import com.wedent.clinic.admin.dto.AdminRoleAssignRequest;
import com.wedent.clinic.admin.dto.AdminRoleSummary;
import com.wedent.clinic.admin.dto.AdminUserCreateRequest;
import com.wedent.clinic.admin.dto.AdminUserResponse;
import com.wedent.clinic.admin.dto.AdminUserUpdateRequest;
import com.wedent.clinic.admin.dto.DoctorProfilePayload;
import com.wedent.clinic.admin.service.AdminUserService;
import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.DoctorProfileRepository;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.RoleRepository;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PW_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PW_LENGTH = 12;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClinicRepository clinicRepository;
    private final EmployeeRepository employeeRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;

    // ─────────────────────────────── reads ───────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String search, RoleType role, UserStatus status,
                                                  Long clinicId, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        // Guard against cross-tenant clinicId injection: if the caller passes
        // a clinicId, make sure it actually lives under their company before
        // we honour it in the filter. Silently ignoring it would leak
        // existence ("this id returned empty"); a 404 is clearer.
        if (clinicId != null) {
            clinicRepository.findByIdAndCompanyId(clinicId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
        }
        String normalized = (search == null || search.isBlank()) ? null : search.trim();
        Page<User> page = userRepository.search(companyId, clinicId, status, role, normalized, pageable);

        // One batched employee lookup instead of N per-row lookups. Keyed
        // by user id since Employee.user is a OneToOne (at most one row per
        // user). DoctorProfile is deliberately not loaded for list rows —
        // the detail endpoint fetches it on demand.
        List<Long> userIds = page.getContent().stream().map(User::getId).toList();
        Map<Long, Employee> employeeByUserId = userIds.isEmpty()
                ? Map.of()
                : employeeRepository.findAllByUserIdsAndCompanyId(userIds, companyId).stream()
                        .collect(Collectors.toMap(
                                e -> e.getUser().getId(),
                                e -> e,
                                (a, b) -> a));
        List<AdminUserResponse> rows = page.getContent().stream()
                .map(u -> toResponse(u, employeeByUserId.get(u.getId()), null, null))
                .toList();
        return new PageResponse<>(
                rows,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getById(Long userId) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);
        return hydrate(user, companyId, null);
    }

    // ─────────────────────────────── writes ───────────────────────────────

    @Override
    public AdminUserResponse create(AdminUserCreateRequest request, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();

        // Resolve clinic + verify it belongs to the caller's company in a
        // single step — otherwise a malicious admin could attach a user to
        // another tenant's clinic.
        Clinic clinic = clinicRepository.findByIdAndCompanyId(request.clinicId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", request.clinicId()));
        SecurityUtils.verifyClinicAccess(clinic.getId());

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }

        Set<Role> roles = loadRoles(request.roleIds());
        validateDoctorRoleAlignment(roles, request.employeeType());

        String tempPassword = generateTemporaryPassword();

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .status(UserStatus.ACTIVE)
                .company(clinic.getCompany())
                .clinic(clinic)
                .roles(new HashSet<>(roles))
                .build();
        user = userRepository.save(user);

        Employee employee = Employee.builder()
                .company(clinic.getCompany())
                .clinic(clinic)
                .user(user)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(request.phone())
                .employeeType(request.employeeType())
                .status(EmployeeStatus.ACTIVE)
                .build();
        employee = employeeRepository.save(employee);

        DoctorProfile doctorProfile = null;
        if (request.employeeType() == EmployeeType.DOCTOR) {
            doctorProfile = DoctorProfile.builder()
                    .employee(employee)
                    .specialty(request.doctorProfile() != null ? request.doctorProfile().specialty() : null)
                    .commissionRate(request.doctorProfile() != null ? request.doctorProfile().commissionRate() : null)
                    .fixedSalary(request.doctorProfile() != null ? request.doctorProfile().fixedSalary() : null)
                    .build();
            doctorProfile = doctorProfileRepository.save(doctorProfile);
        }

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(clinic.getId())
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .detail(Map.of(
                        "email", email,
                        "employeeType", request.employeeType().name(),
                        "roleCodes", roles.stream().map(r -> r.getCode().name()).sorted().toList()
                ))
                .build());
        log.info("Admin user created userId={} companyId={} clinicId={}",
                user.getId(), companyId, clinic.getId());

        AdminUserResponse response = toResponse(user, employee, doctorProfile, tempPassword);
        return response;
    }

    @Override
    public AdminUserResponse update(Long userId, AdminUserUpdateRequest request, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);
        Employee employee = employeeRepository.findByUserIdAndCompanyId(userId, companyId).orElse(null);

        Map<String, Object> diff = new LinkedHashMap<>();

        if (request.firstName() != null && !request.firstName().isBlank()) {
            diffField(diff, "firstName", user.getFirstName(), request.firstName().trim());
            user.setFirstName(request.firstName().trim());
            if (employee != null) employee.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            diffField(diff, "lastName", user.getLastName(), request.lastName().trim());
            user.setLastName(request.lastName().trim());
            if (employee != null) employee.setLastName(request.lastName().trim());
        }
        if (request.phone() != null && employee != null) {
            diffField(diff, "phone", employee.getPhone(), request.phone());
            employee.setPhone(request.phone());
        }
        if (request.clinicId() != null) {
            Clinic newClinic = clinicRepository.findByIdAndCompanyId(request.clinicId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Clinic", request.clinicId()));
            // Re-assigning a user to another clinic is an OWNER-scope action
            // in practice (MANAGERs are clinic-bounded); since the PreAuthorize
            // layer already gates the endpoint, we just enforce scope here.
            SecurityUtils.verifyClinicAccess(newClinic.getId());
            if (user.getClinic() == null || !Objects.equals(user.getClinic().getId(), newClinic.getId())) {
                diffField(diff, "clinicId",
                        user.getClinic() != null ? user.getClinic().getId() : null,
                        newClinic.getId());
                user.setClinic(newClinic);
                if (employee != null) employee.setClinic(newClinic);
            }
        }
        if (request.employeeType() != null && employee != null) {
            if (employee.getEmployeeType() != request.employeeType()) {
                diffField(diff, "employeeType",
                        employee.getEmployeeType().name(),
                        request.employeeType().name());
                employee.setEmployeeType(request.employeeType());
            }
            validateDoctorRoleAlignment(user.getRoles(), request.employeeType());
        }
        if (request.doctorProfile() != null && employee != null) {
            applyDoctorProfile(employee, request.doctorProfile(), diff);
        }

        userRepository.save(user);
        if (employee != null) employeeRepository.save(employee);

        if (!diff.isEmpty()) {
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_UPDATED)
                    .actorUserId(SecurityUtils.currentUserIdOrNull())
                    .actorEmail(SecurityUtils.currentUser().email())
                    .companyId(companyId)
                    .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                    .targetType("User")
                    .targetId(user.getId())
                    .ipAddress(ipAddress)
                    .detail(Map.of("changes", diff))
                    .build());
        }

        DoctorProfile profile = employee != null
                ? doctorProfileRepository.findByEmployeeId(employee.getId()).orElse(null)
                : null;
        return toResponse(user, employee, profile, null);
    }

    @Override
    public AdminUserResponse assignRoles(Long userId, AdminRoleAssignRequest request, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);

        Set<Role> toAdd = loadRoles(request.roleIds());
        Set<RoleType> before = snapshotRoleCodes(user);
        user.getRoles().addAll(toAdd);
        Set<RoleType> after = snapshotRoleCodes(user);

        if (before.equals(after)) {
            // Idempotent: nothing changed; skip the audit noise.
            return hydrate(user, companyId, null);
        }

        userRepository.save(user);
        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_ROLE_CHANGED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .detail(Map.of("op", "assign", "from", before, "to", after))
                .build());
        return hydrate(user, companyId, null);
    }

    @Override
    public AdminUserResponse removeRole(Long userId, Long roleId, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        // Last-owner guard: if stripping CLINIC_OWNER would leave the company
        // with zero active owners, refuse. This is the only place an admin
        // can accidentally orphan a tenant, so the guard has to live here
        // rather than bubble up from the DB.
        if (role.getCode() == RoleType.CLINIC_OWNER
                && user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().stream().anyMatch(r -> r.getCode() == RoleType.CLINIC_OWNER)
                && userRepository.countActiveOwners(companyId) <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot remove the last active CLINIC_OWNER role from the company");
        }

        Set<RoleType> before = snapshotRoleCodes(user);
        boolean removed = user.getRoles().removeIf(r -> r.getId().equals(roleId));
        if (!removed) {
            return hydrate(user, companyId, null);
        }
        userRepository.save(user);
        Set<RoleType> after = snapshotRoleCodes(user);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_ROLE_CHANGED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .detail(Map.of("op", "remove", "roleCode", role.getCode().name(),
                        "from", before, "to", after))
                .build());
        return hydrate(user, companyId, null);
    }

    @Override
    public AdminUserResponse activate(Long userId, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return hydrate(user, companyId, null);
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_ACTIVATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .build());
        return hydrate(user, companyId, null);
    }

    @Override
    public AdminUserResponse deactivate(Long userId, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);

        // Self-lockout guard — refusing to disable yourself is a friendlier
        // UX than letting the admin nuke their own session and then having
        // to call support.
        Long self = SecurityUtils.currentUserIdOrNull();
        if (self != null && self.equals(user.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You cannot deactivate your own account");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            return hydrate(user, companyId, null);
        }

        // Last-owner guard: disabling the final active CLINIC_OWNER would
        // orphan the tenant's manage-surface.
        if (user.getRoles().stream().anyMatch(r -> r.getCode() == RoleType.CLINIC_OWNER)
                && userRepository.countActiveOwners(companyId) <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot deactivate the last active CLINIC_OWNER of the company");
        }

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllForUser(user.getId());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_DISABLED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .detail(Map.of("revokedSessions", revoked))
                .build());
        log.info("Admin deactivated userId={} revokedSessions={}", user.getId(), revoked);
        return hydrate(user, companyId, null);
    }

    @Override
    public AdminResetPasswordResponse resetPassword(Long userId, String ipAddress) {
        Long companyId = SecurityUtils.currentCompanyId();
        User user = loadInScope(userId, companyId);

        String tempPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllForUser(user.getId());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_PASSWORD_RESET)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .actorEmail(SecurityUtils.currentUser().email())
                .companyId(companyId)
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .targetType("User")
                .targetId(user.getId())
                .ipAddress(ipAddress)
                .detail(Map.of("revokedSessions", revoked))
                .build());
        log.info("Admin reset password userId={} revokedSessions={}", user.getId(), revoked);
        return new AdminResetPasswordResponse(tempPassword);
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private User loadInScope(Long userId, Long companyId) {
        return userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** Resolves role ids, rejecting any that do not exist. No cross-tenant
     *  dimension to check — roles are global. */
    private Set<Role> loadRoles(Set<Long> ids) {
        List<Role> found = roleRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            Set<Long> foundIds = found.stream().map(Role::getId).collect(Collectors.toSet());
            Set<Long> missing = new HashSet<>(ids);
            missing.removeAll(foundIds);
            throw new ResourceNotFoundException("Role(s) not found: " + missing);
        }
        return new HashSet<>(found);
    }

    /** Doctor/role alignment rule: if the employee type is DOCTOR we require
     *  the user to also hold the DOCTOR role, otherwise the payout service
     *  will never be able to look them up by role. */
    private void validateDoctorRoleAlignment(Set<Role> roles, EmployeeType type) {
        if (type == EmployeeType.DOCTOR
                && roles.stream().noneMatch(r -> r.getCode() == RoleType.DOCTOR)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "DOCTOR employee type requires the DOCTOR role");
        }
    }

    private void applyDoctorProfile(Employee employee, DoctorProfilePayload payload,
                                    Map<String, Object> diff) {
        DoctorProfile profile = doctorProfileRepository.findByEmployeeId(employee.getId())
                .orElse(null);
        if (profile == null && employee.getEmployeeType() != EmployeeType.DOCTOR) {
            // Non-doctor + no existing profile → nothing to do.
            return;
        }
        boolean created = profile == null;
        if (created) {
            profile = DoctorProfile.builder().employee(employee).build();
        }
        if (payload.specialty() != null) {
            diffField(diff, "specialty", profile.getSpecialty(), payload.specialty());
            profile.setSpecialty(payload.specialty());
        }
        if (payload.commissionRate() != null) {
            diffField(diff, "commissionRate", profile.getCommissionRate(), payload.commissionRate());
            profile.setCommissionRate(payload.commissionRate());
        }
        if (payload.fixedSalary() != null) {
            diffField(diff, "fixedSalary", profile.getFixedSalary(), payload.fixedSalary());
            profile.setFixedSalary(payload.fixedSalary());
        }
        doctorProfileRepository.save(profile);
    }

    private static void diffField(Map<String, Object> diff, String key, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("from", before);
            entry.put("to", after);
            diff.put(key, entry);
        }
    }

    /** Generates a 12-char temp password from a human-friendly alphabet
     *  (omits I/l/O/0/1 to avoid read-aloud confusion). */
    private static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PW_LENGTH);
        for (int i = 0; i < TEMP_PW_LENGTH; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static Set<RoleType> snapshotRoleCodes(User user) {
        return user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private AdminUserResponse hydrate(User user, Long companyId, String tempPassword) {
        Employee employee = employeeRepository.findByUserIdAndCompanyId(user.getId(), companyId).orElse(null);
        DoctorProfile profile = employee != null
                ? doctorProfileRepository.findByEmployeeId(employee.getId()).orElse(null)
                : null;
        return toResponse(user, employee, profile, tempPassword);
    }

    private static AdminUserResponse toResponse(User user, Employee employee, DoctorProfile profile,
                                                String tempPassword) {
        Set<AdminRoleSummary> roles = user.getRoles().stream()
                .map(r -> new AdminRoleSummary(r.getId(), r.getCode()))
                .collect(Collectors.toCollection(() ->
                        new java.util.TreeSet<AdminRoleSummary>(Comparator.comparing(s -> s.code().name()))));
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                employee != null ? employee.getPhone() : null,
                user.getStatus(),
                user.getCompany().getId(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                user.getClinic() != null ? user.getClinic().getName() : null,
                employee != null ? employee.getId() : null,
                employee != null ? employee.getEmployeeType() : null,
                profile != null ? profile.getId() : null,
                profile != null ? profile.getSpecialty() : null,
                profile != null ? profile.getCommissionRate() : null,
                profile != null ? profile.getFixedSalary() : null,
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                tempPassword
        );
    }
}
