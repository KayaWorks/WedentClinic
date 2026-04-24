# WedentClinic Backend — İşlev Dokümantasyonu

**Proje:** WedentClinic (multi-tenant diş kliniği SaaS)
**Yığın:** Java 21 · Spring Boot 3.3.5 · PostgreSQL 16 · Flyway · JPA + MapStruct · Spring Security (stateless JWT) · Redis (refresh-token) · Swagger/OpenAPI

---

## 1. Mimari Özet

WedentClinic multi-tenant bir sistem. Üst seviye hiyerarşi:

```
Company (tenant)
  └── Clinic (şube)
        ├── Employee  (DOCTOR / MANAGER / STAFF / ASSISTANT)
        │     └── DoctorProfile (commissionRate, specialty)
        ├── Patient
        │     └── Treatment  (feeds Payout)
        ├── Appointment
        └── PayoutPeriod  (hakediş)
              └── PayoutDeduction
```

**Ortak desenler:**
- Tüm iş entity'leri `BaseEntity`'den kalıtım alır: `id / createdAt / updatedAt / createdBy / updatedBy / active / version` (optimistic locking için `@Version`).
- Soft-delete: `@SQLDelete + @SQLRestriction("active = true")`.
- Tenant izolasyonu çift katmanlı:
  1. Controller'da `@PreAuthorize` ile rol gating.
  2. Servis katmanında `SecurityUtils.currentCompanyId()` + `verifyClinicAccess()` ile defansif sınır kontrolü.
- Hata kodları `ErrorCode` enum'una bağlı (`ACCESS_DENIED=403`, `BUSINESS_RULE_VIOLATION=422`, `RESOURCE_NOT_FOUND=404`, `DUPLICATE_RESOURCE=409`, `INVALID_REQUEST=400`).
- Tüm yazma işlemleri `AuditEvent`'a düşer; `AuditEventType` enum'u closed-set olarak tutulur.
- Her endpoint `ApiResponse<T>` zarfıyla, sayfalı okumalar `PageResponse<T>` ile döner.

**Roller** (`SecurityUtils` sabitleri): `CLINIC_OWNER`, `MANAGER`, `DOCTOR`, `STAFF`.

---

## 2. Kimlik Doğrulama ve Oturum

### 2.1 Giriş / Çıkış / Token Yenileme (mevcut altyapı)
- JWT access-token + Redis'te saklanan SHA-256 hash'li refresh-token.
- Refresh rotation replay tespiti (`TOKEN_REFRESH_REPLAY` audit).
- `AuthenticatedUser` principal: `(userId, email, companyId, clinicId, Set<String> roles, authorities)`.

### 2.2 Aktif Oturum Listesi ve Cihaz Kapatma *(Task #7 — eklendi)*

**Amaç:** Kullanıcının kendi aktif oturumlarını görmesi ve istediği cihazı kapatması.

**Endpoint'ler (`/api/users/me/sessions`):**

| Metod | Yol | Açıklama | Roller |
|---|---|---|---|
| GET | `/api/users/me/sessions` | Kullanıcının aktif oturum listesi (issuedAt, expiresAt, ipAddress, userAgent) | 4 rol |
| DELETE | `/api/users/me/sessions/{sessionId}` | Tek bir oturumu kapat (204 No Content) | 4 rol |
| DELETE | `/api/users/me/sessions` | Kendine ait tüm diğer oturumları kapat (`revokedCount` döner) | 4 rol |

**Güvenlik notları:**
- `sessionId` = refresh-token'ın SHA-256 hex'i (tek yönlü, kimlik sızdırmaz).
- Audit `detail`'inde sadece ilk 8 karakter (`sessionIdPrefix`) loglanır — tam hash log'a düşmez.
- `revokeSession` yatay yükseltmeye karşı `record.userId`'yi karşılaştırır: A kullanıcısı B'nin hash'ini tahmin etse bile B'nin oturumunu kapatamaz.
- Dangling SET entry'ler listeleme sırasında otomatik süpürülür.

**Yeni AuditEventType:** `SESSION_REVOKED`, `SESSIONS_REVOKED_ALL`.

---

## 3. Kullanıcı & Rol Katalog Endpoint'leri *(Task #1)*

| Metod | Yol | Açıklama | Roller |
|---|---|---|---|
| GET | `/api/roles` | Tenant'a tanımlı tüm rolleri döner | CLINIC_OWNER, MANAGER |
| GET | `/api/permissions` | Permission kataloğu | CLINIC_OWNER, MANAGER |

Frontend tarafında rol seçim ekranları ve admin ekranları için gerekli.

---

## 4. Clinic & Company Okuma *(Task #2)*

| Metod | Yol | Açıklama |
|---|---|---|
| GET | `/api/clinics` | Tenant altındaki klinikler (non-owner için kendi kliniği filtrelenir) |
| GET | `/api/clinics/{id}` | Tek klinik detayı |
| GET | `/api/companies/current` | Giriş yapan tenant'ın company bilgisi |

Tenant izolasyonu servis katmanında sağlanır; cross-tenant sorgular `ResourceNotFoundException` olarak döner (varlık sızdırmaz).

---

## 5. Clinic CRUD *(Task #3)*

| Metod | Yol | İşlem | Roller |
|---|---|---|---|
| POST | `/api/clinics` | Yeni klinik oluştur | CLINIC_OWNER |
| PATCH | `/api/clinics/{id}` | Metadata güncelle | CLINIC_OWNER |
| DELETE | `/api/clinics/{id}` | Soft-delete | CLINIC_OWNER |

**Kurallar:**
- Klinik daima login yapan owner'ın company'sine bağlı oluşur; body'de farklı `companyId` geçirilemez.
- Güncelleme/delete öncesi tenant sınırı zorlanır.
- Audit: `CLINIC_CREATED`, `CLINIC_UPDATED`, `CLINIC_DELETED`.

---

## 6. Company Profil Güncellemesi *(Task #4)*

| Metod | Yol | Açıklama |
|---|---|---|
| PATCH | `/api/companies/current` | Company profilini güncelle |

Düzenlenebilir alanlar: `name`, `legalName`, `taxNumber`, `taxOffice`, `phone`, `email`, `address`, `city`, `district`, `website`, `logoUrl`, `timezone`.
Değiştirilemez: `id`, `ownerId`, `createdAt`, `tenantKey/slug`, subscription alanları.

**Audit:** `COMPANY_UPDATED` (before/after diff detail'de).

> **Not:** FE'nin beklediği `/api/companies/me` alias'ı (GET + PATCH) henüz eklenmedi — sıradaki sprint'te geliyor.

---

## 7. Dashboard Filtreleri *(Task #5)*

Dashboard endpoint'i `?clinicId=...&doctorId=...` ile filtrelenebilir. Non-owner rollere otomatik olarak kendi klinik scope'u zorlanır (FE'den gelen `clinicId` parametresi yok sayılır).

---

## 8. Kullanıcı Profili ve Bildirim Tercihleri *(Task #6)*

### 8.1 User Profile (`/api/users/me`)

| Metod | Yol | Açıklama |
|---|---|---|
| GET | `/api/users/me` | Oturumdaki kullanıcının profili |
| PATCH | `/api/users/me` | firstName/lastName/phone/email gibi profil alanları |

### 8.2 Preferences (`/api/users/me/preferences`)

| Metod | Yol | Açıklama |
|---|---|---|
| GET | `/api/users/me/preferences` | Dil / tema / bildirim kanalları + kategori bildirimleri |
| PATCH | `/api/users/me/preferences` | Kısmi güncelleme |

**Depolama deseni:**
- `UserPreferences` tablosu; notifications JSONB (`@JdbcTypeCode(SqlTypes.JSON)` + String storage, H2-test uyumlu).
- **Lazy-create:** Row yoksa GET default sentetik yanıt döner, ilk PATCH'te satır oluşur.
- Kanallar (email/push/sms) ve kategori bildirimleri (appointment_reminder, payment_due, …) ayrı ayrı null-skip merge mantığıyla güncellenir.

**Migration:** `V8__user_preferences.sql`.

---

## 9. Tedavi Modülü *(Task #8)*

### 9.1 Domain

Her tedavi bir hasta üzerinde bir hekimin yaptığı işlem. `fee` + `doctorId` + `completedAt` bilgisi hakediş (payout) hesaplamasının hammaddesi.

**Status akışı:** `PLANNED → COMPLETED → CANCELLED` (geri dönüşler serbest; sadece payout-locked tedaviler donar).

### 9.2 Endpoint'ler

**Hasta-kapsamlı (yazma + listeleme):**

| Metod | Yol | Açıklama | Roller |
|---|---|---|---|
| POST | `/api/patients/{patientId}/treatments` | Tedavi oluştur (201) | OWNER, MANAGER, DOCTOR |
| GET | `/api/patients/{patientId}/treatments` | Hastanın tedavi geçmişi (newest-first, paged) | 4 rol |

**Flat by-id (cross-patient navigasyon, FE deep-link için):**

| Metod | Yol | Açıklama | Roller |
|---|---|---|---|
| GET | `/api/treatments/{id}` | Tek tedavi detayı | 4 rol |
| PATCH | `/api/treatments/{id}` | Tedavi güncelle (payout-locked ise reddedilir) | OWNER, MANAGER, DOCTOR |
| DELETE | `/api/treatments/{id}` | Soft-delete (payout-locked ise reddedilir) | OWNER, MANAGER |

### 9.3 Önemli Kurallar

- Tedavi daima `patient.company` ve `patient.clinic`'i miras alır; body bunları override edemez.
- Hekim seçimi: `EmployeeRepository.findByIdAndCompanyIdAndEmployeeType(id, companyId, DOCTOR)` + `EmployeeStatus.ACTIVE` zorunlu.
- `isPayoutLocked() == true` olan tedavilerde update/delete → `BUSINESS_RULE_VIOLATION`. Bu, payouts modülünün güvendiği sözleşmedir: issued bir hakediş sonradan değişmesin.
- Status → `COMPLETED` geçişinde `completedAt` otomatik stamp'lenir; geri alınırsa temizlenir. Hakediş aggregation'ı `performedAt`'a değil, `completedAt`'a bakar.
- Varsayılanlar: currency = `TRY`, status = `PLANNED`.

### 9.4 Audit

- `TREATMENT_CREATED`, `TREATMENT_UPDATED`, `TREATMENT_STATUS_CHANGED` (status değiştiyse ayrı event tipi), `TREATMENT_DELETED`.
- Update event'inin `detail` alanında field-level diff: `{"fee":{"from":500,"to":750}, "status":{"from":"PLANNED","to":"COMPLETED"}}`.

### 9.5 Migration (`V9__treatments.sql`)

```sql
treatments (
  id, company_id, clinic_id, patient_id, doctor_id,
  name, tooth_number, notes,
  performed_at, fee (>0), currency (def 'TRY'),
  status IN ('PLANNED','COMPLETED','CANCELLED'),
  payout_locked_at, +BaseEntity
)
```

Index'ler: `(patient_id, performed_at DESC)`, `(doctor_id, status, performed_at)`, `(clinic_id, performed_at)`, `(company_id)`.

---

## 10. Hakediş (Payout) Modülü *(Task #9)*

### 10.1 Domain Kuralları

- Hakediş **doktor + dönem** (`[periodStart, periodEnd)`) bazında hesaplanır.
- Sadece `COMPLETED` + `payoutLockedAt IS NULL` olan tedaviler gross'a dahil.
- **Gross** = sum(tedavi ücretleri) × `DoctorProfile.commissionRate / 100` (0-100 arası yüzde).
- **Net** = gross − sum(deductions).
- Kesinti tipleri: `LAB`, `MATERIAL`, `ADVANCE_PAYMENT`, `PENALTY`, `OTHER`.

### 10.2 Lifecycle

```
DRAFT ──► APPROVED ──► PAID       (happy path)
  └─────► CANCELLED               (vazgeçilen draft)
```

- **DRAFT**: recalculate edilebilir, deduction eklenip çıkarılabilir.
- **APPROVED**: Immutable. Approve tek transaction'da:
  1. Eligible tedaviler yeniden sorgulanır.
  2. `commissionRateSnapshot` + `treatmentTotalSnapshot` donar.
  3. Dahil edilen her tedavinin `payoutLockedAt` + `payoutPeriod` stamp'lenir.
- **PAID**: `paidAt` set edilir, terminal.
- **CANCELLED**: Sadece DRAFT'tan geçiş, terminal.

**Kritik invariant:** Approve sonrası ne `DoctorProfile.commissionRate`'ın ne de kaynak tedavinin sonradan değişmesi issued payout'u bozabilir — snapshot + locking kombinasyonuyla garantilenir.

### 10.3 Endpoint'ler (`/api/payouts`)

| Metod | Yol | Açıklama | Roller |
|---|---|---|---|
| POST | `/api/payouts/draft` | Doktor + dönem için DRAFT oluştur | OWNER, MANAGER |
| GET | `/api/payouts` | Filtreli liste (`doctorProfileId`, `status`, `periodStart`, `periodEnd`) | 4 rol |
| GET | `/api/payouts/{id}` | Detay: deductions + dahil tedavi kalemleri | 4 rol |
| POST | `/api/payouts/{id}/deductions` | DRAFT'a kesinti ekle | OWNER, MANAGER |
| DELETE | `/api/payouts/{id}/deductions/{deductionId}` | Kesinti kaldır | OWNER, MANAGER |
| POST | `/api/payouts/{id}/recalculate` | DRAFT'ı güncel tedavi verisine göre yeniden hesapla | OWNER, MANAGER |
| POST | `/api/payouts/{id}/approve` | APPROVE: tedavileri kilitler + snapshot dondurur | OWNER, MANAGER |
| POST | `/api/payouts/{id}/mark-paid` | APPROVED → PAID | OWNER, MANAGER |
| POST | `/api/payouts/{id}/cancel` | DRAFT → CANCELLED | OWNER, MANAGER |

Non-owner kullanıcılar otomatik olarak kendi klinik scope'una filtrelenir.

### 10.4 Validasyon / Koruma Kontrolleri

- `periodStart < periodEnd` zorunlu, yoksa `INVALID_REQUEST`.
- Deduction amount > 0 (Bean-Validation + DB CHECK).
- Cross-tenant DoctorProfile talebi → varlık sızdırmadan `RESOURCE_NOT_FOUND`.
- Inactive doktor → `BUSINESS_RULE_VIOLATION`.
- Commission rate null olan doktor approve edilemez.
- Empty payout approve edilemez.
- Zaten locked tedaviyi approve'a sokma girişimi → defansif kontrol, reddedilir.
- APPROVED/PAID payout üzerinde deduction değişikliği ya da recalc yok.
- DRAFT iken `mark-paid` yok; APPROVED değilken `cancel` yok.

### 10.5 Audit Event'leri

- `PAYOUT_DRAFT_CREATED`, `PAYOUT_RECALCULATED`, `PAYOUT_APPROVED`, `PAYOUT_MARKED_PAID`, `PAYOUT_CANCELLED`.
- `PAYOUT_DEDUCTION_ADDED`, `PAYOUT_DEDUCTION_REMOVED`.
- Detail alanlarında doctorProfileId, grossAmount, netAmount, lockedTreatmentCount, from/after gibi stabil anahtarlar tutulur.

### 10.6 Migration (`V10__payouts.sql`)

- `treatments`: `completed_at TIMESTAMPTZ`, `payout_period_id BIGINT` eklenmesi; eski COMPLETED kayıtlar için `completed_at ← performed_at` backfill.
- `payout_periods`: snapshot kolonları, status CHECK, `period_start < period_end` CHECK, BaseEntity kolonları.
- `payout_deductions`: type CHECK, amount > 0 CHECK, `ON DELETE CASCADE`.
- Hot-path index'leri: `(doctor_id, status, completed_at)`, `(payout_period_id)`, `(doctor_profile_id, status)`, `(company_id, period_start DESC)`, `(clinic_id, status, period_start)`.

### 10.7 Testler

- `PayoutCalculatorTest` — 7 senaryo: zero, normal yüzde, deduction, negative net, null rate, HALF_UP rounding, null fee.
- `PayoutServiceImplTest` — 13 senaryo: happy path draft/approve/markPaid/cancel + tüm lifecycle guard'ları + cross-tenant leak + inactive doctor + empty approve + race detection + no-commission-rate + deduction add/remove.

---

## 11. Ortak Altyapı

### 11.1 Exception Hiyerarşisi
- `BusinessException` (base) → `ResourceNotFoundException`, `DuplicateResourceException`, `TenantScopeViolationException`, `InvalidCredentialsException`, `AppointmentConflictException`.
- `GlobalExceptionHandler` `ApiResponse.error(...)` formatına çevirir; `VALIDATION_ERROR` için alan bazlı `fieldErrors` listesi üretir.

### 11.2 Audit Pipeline
- `AuditEventPublisher.publish(AuditEvent.builder(TYPE)...)` → Spring `ApplicationEventPublisher` → async listener DB'ye yazar.
- Her event MDC'den `traceId` ile zenginleşir, log satırlarıyla 1:1 korelasyon sağlar.
- Writer asla request akışını bozmaz.

### 11.3 MapStruct `CommonMapperConfig`
- `componentModel = spring`, `builder disabled`, `unmappedTargetPolicy = IGNORE`, `nullValuePropertyMappingStrategy = IGNORE` (PATCH semantiği için).

### 11.4 Flyway Migration Özeti

| Version | İçerik |
|---|---|
| V1 | init base (companies, clinics, users, roles, permissions) |
| V2 | RBAC seed (default roller ve permission'lar) |
| V3 | employees + doctor_profiles |
| V4 | patients |
| V5 | appointments |
| V6 | hot-path indexes |
| V7 | audit_log (JSONB detail) |
| V8 | user_preferences |
| V9 | treatments |
| V10 | payouts (periods + deductions) + treatments extension |

---

## 12. Sıradaki Sprint — Bekleyen İşler

- **Admin User Management** (`/api/admin/users`): list/get/create/patch + rol assign/remove + activate/deactivate + opsiyonel reset-password. "Son CLINIC_OWNER kaldırılamaz" kuralı + tenant izolasyonu.
- **Company `/me` alias**: `GET /api/companies/me` ve `PATCH /api/companies/me` (mevcut `/current` uçları korunur).
- **Appointment recurrence + calendar view**: tekrarlı randevular ve takvim görünüm endpoint'i.
