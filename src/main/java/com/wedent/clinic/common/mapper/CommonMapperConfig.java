package com.wedent.clinic.common.mapper;

import org.mapstruct.Builder;
import org.mapstruct.MapperConfig;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration for every mapper in the application.
 *
 * <p>Rationale:
 * <ul>
 *     <li>{@code componentModel = "spring"} — generated mappers become Spring beans and can be
 *         constructor-injected through Lombok's {@code @RequiredArgsConstructor}.</li>
 *     <li>{@code builder = @Builder(disableBuilder = true)} — Lombok {@code @Builder} on entities
 *         does not expose fields inherited from {@link com.wedent.clinic.common.entity.BaseEntity}
 *         (id, createdAt, updatedAt, createdBy, updatedBy, active, version). Disabling the builder
 *         forces MapStruct to construct targets via no-arg constructor + setters, which BaseEntity
 *         already provides via Lombok {@code @Setter}.</li>
 *     <li>{@code unmappedTargetPolicy = IGNORE} — inherited audit fields and relational fields
 *         (company, clinic, user) are deliberately set in the service layer, not by the mapper.
 *         Ignoring unmapped targets keeps mapper interfaces terse without sacrificing safety,
 *         since responses are DTO records whose compilation enforces shape.</li>
 *     <li>{@code nullValuePropertyMappingStrategy = IGNORE} — update mappers will not overwrite
 *         existing properties with {@code null} when the incoming DTO omits them, giving PATCH-like
 *         semantics for partial updates.</li>
 * </ul>
 */
@MapperConfig(
        componentModel = "spring",
        builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CommonMapperConfig {
}
