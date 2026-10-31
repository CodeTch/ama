-- C-01 local media preparation. S3/HA wiring remains a later phase boundary.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE media_source (
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `ordinal` bigint unsigned NOT NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `container` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `duration_ms` bigint unsigned NOT NULL,
  `sample_rate` int unsigned NOT NULL,
  `channels` int unsigned NOT NULL,
  `bits_per_sample` int unsigned NOT NULL,
  `has_audio` boolean NOT NULL,
  `source_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`batch_id`, `ordinal`),
  UNIQUE INDEX media_source_file (`batch_id`, `file_id`),
  CONSTRAINT ck_media_source_0 CHECK (`ordinal` > 0),
  CONSTRAINT ck_media_source_1 CHECK (`duration_ms` >= 0),
  CONSTRAINT ck_media_source_2 CHECK (`sample_rate` >= 0 AND `channels` >= 0 AND `bits_per_sample` >= 0),
  CONSTRAINT ck_media_source_3 CHECK (`source_sha256` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT fk_media_source_batch FOREIGN KEY (`batch_id`) REFERENCES input_batch (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_media_source_file FOREIGN KEY (`file_id`) REFERENCES file_object (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE media_source_map (
  `batch_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `file_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `normalized_start_sample` bigint unsigned NOT NULL,
  `sample_count` bigint unsigned NOT NULL,
  PRIMARY KEY (`batch_id`, `file_id`),
  CONSTRAINT ck_media_source_map_0 CHECK (`sample_count` > 0),
  CONSTRAINT fk_media_source_map_batch FOREIGN KEY (`batch_id`) REFERENCES input_batch (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_media_source_map_file FOREIGN KEY (`file_id`) REFERENCES file_object (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
