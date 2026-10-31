-- An identity-local shared clock floor. NULL keeps legacy sessions on the original DB-time gate.
-- Only genuine issuance/resolution advances this value under the existing identity lock.
-- Never derive it from client timestamps or from arbitrary stored session dates.
ALTER TABLE enterprise_identity ADD COLUMN session_time_floor DATETIME(6) NULL;
