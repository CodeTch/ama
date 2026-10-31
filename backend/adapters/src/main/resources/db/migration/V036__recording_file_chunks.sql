-- New chunks pin immutable FileStoragePort keys. Historical database PCM remains explicit.
ALTER TABLE recording_chunk ADD COLUMN pcm_storage_key VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NULL;
