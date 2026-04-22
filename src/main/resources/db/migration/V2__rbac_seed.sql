-- =============================================================
-- V2: Seed roles and default permissions
-- =============================================================

INSERT INTO roles (code, description) VALUES
    ('CLINIC_OWNER', 'Company owner with full access across all clinics'),
    ('MANAGER',      'Clinic manager with operational authority'),
    ('DOCTOR',       'Practitioner with patient and appointment access'),
    ('STAFF',        'Front-desk / assistant role');

INSERT INTO permissions (code, description) VALUES
    ('CLINIC_READ',        'Read clinic data'),
    ('CLINIC_MANAGE',      'Manage clinics'),
    ('EMPLOYEE_READ',      'Read employees'),
    ('EMPLOYEE_MANAGE',    'Create / update / delete employees'),
    ('PATIENT_READ',       'Read patients'),
    ('PATIENT_MANAGE',     'Create / update / delete patients'),
    ('APPOINTMENT_READ',   'Read appointments'),
    ('APPOINTMENT_MANAGE', 'Create / update / delete appointments');

-- CLINIC_OWNER: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'CLINIC_OWNER';

-- MANAGER: all except clinic manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'MANAGER'
  AND p.code IN ('CLINIC_READ','EMPLOYEE_READ','EMPLOYEE_MANAGE',
                 'PATIENT_READ','PATIENT_MANAGE',
                 'APPOINTMENT_READ','APPOINTMENT_MANAGE');

-- DOCTOR: read employees, manage appointments and patients (own scope enforced in app layer)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'DOCTOR'
  AND p.code IN ('CLINIC_READ','EMPLOYEE_READ',
                 'PATIENT_READ','PATIENT_MANAGE',
                 'APPOINTMENT_READ','APPOINTMENT_MANAGE');

-- STAFF: patient + appointment operations
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'STAFF'
  AND p.code IN ('CLINIC_READ','PATIENT_READ','PATIENT_MANAGE',
                 'APPOINTMENT_READ','APPOINTMENT_MANAGE');
