-- Personal-template removal hides future choices; immutable revisions remain referenceable.
CREATE TABLE personal_template_state (
  template_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  command_revision bigint unsigned NOT NULL,
  deleted_at datetime(6) NULL,
  PRIMARY KEY (template_id),
  INDEX personal_template_owner (owner_id,template_id),
  CONSTRAINT fk_personal_template_parent FOREIGN KEY (template_id) REFERENCES minutes_template(id),
  CONSTRAINT fk_personal_template_owner FOREIGN KEY (owner_id) REFERENCES enterprise_identity(enterprise_id),
  CONSTRAINT ck_personal_template_revision CHECK (command_revision > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE personal_template_command_receipt (
  scope_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  resource_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  key_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  http_status smallint unsigned NOT NULL DEFAULT 0,
  result_json longtext NULL,
  result_sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (scope_hash),
  CONSTRAINT fk_personal_template_command_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id),
  CONSTRAINT ck_personal_template_command_operation CHECK (operation IN ('CREATE','UPDATE','DELETE')),
  CONSTRAINT ck_personal_template_command_status CHECK (http_status IN (0,200,201)),
  CONSTRAINT ck_personal_template_command_result CHECK ((http_status=0 AND result_json IS NULL AND result_sha256 IS NULL) OR (http_status IN (200,201) AND result_json IS NOT NULL AND result_sha256 IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
