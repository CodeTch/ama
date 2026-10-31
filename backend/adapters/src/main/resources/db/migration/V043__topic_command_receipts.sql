CREATE TABLE topic_command_receipt (
 scope_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
 resource_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 key_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 http_status smallint unsigned NOT NULL DEFAULT 0,
 result_id char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 result_name varchar(255) NULL,
 result_description text NULL,
 result_revision bigint unsigned NULL,
 created_at datetime(6) NOT NULL,
 PRIMARY KEY (scope_hash),
 UNIQUE INDEX topic_command_key (actor_id,resource_id,operation,key_hash),
 CONSTRAINT fk_topic_receipt_actor FOREIGN KEY (actor_id) REFERENCES enterprise_identity(enterprise_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT ck_topic_receipt_status CHECK (http_status IN (0,200,201,204)),
 CONSTRAINT ck_topic_receipt_shape CHECK (
  (http_status IN (0,204) AND result_id IS NULL AND result_name IS NULL AND result_description IS NULL AND result_revision IS NULL)
  OR (http_status IN (200,201) AND result_id IS NOT NULL AND result_name IS NOT NULL AND result_revision IS NOT NULL AND result_revision < 9223372036854775807)
 )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
