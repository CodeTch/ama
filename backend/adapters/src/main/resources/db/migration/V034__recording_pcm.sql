-- Legacy metadata cannot be upgraded to audio. NULL deliberately remains an unreadable legacy row.
ALTER TABLE recording_chunk ADD COLUMN pcm MEDIUMBLOB NULL;
