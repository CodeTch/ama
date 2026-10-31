SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Ordinary-user access is derived from an ACTIVE enterprise_identity. Only
-- exceptional platform roles remain durable assignments.
DELETE FROM role_assignment WHERE role = 'USER';
ALTER TABLE role_assignment DROP CHECK ck_role_assignment_0;
ALTER TABLE role_assignment ADD CONSTRAINT ck_role_assignment_0 CHECK (role IN ('PLATFORM_ADMIN'));
