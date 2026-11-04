SET NAMES utf8mb4;
SET time_zone = '+00:00';
ALTER TABLE meeting_info_suggestion
 DROP CHECK ck_info_suggestion_field,
 ADD COLUMN category_value VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
 ADD COLUMN category_input_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN category_request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN category_profile VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT ck_info_suggestion_field CHECK(field IN ('NAME','SUMMARY','CATEGORY')),
 ADD CONSTRAINT ck_info_suggestion_category CHECK(
  (field<>'CATEGORY' AND category_value IS NULL AND category_input_fingerprint IS NULL AND category_request_sha256 IS NULL AND category_profile IS NULL)
  OR (field='CATEGORY' AND category_value IS NOT NULL AND category_input_fingerprint IS NOT NULL AND category_request_sha256 IS NOT NULL AND category_profile='ama-meeting-category.v1')
 );
