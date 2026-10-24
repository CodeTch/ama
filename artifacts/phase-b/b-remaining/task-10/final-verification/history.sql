SET time_zone='+00:00',lock_wait_timeout=5,innodb_lock_wait_timeout=5,MAX_EXECUTION_TIME=5000;
SELECT installed_rank,version,description,type,script,checksum,installed_by,DATE_FORMAT(installed_on,'%Y-%m-%dT%H:%i:%s') AS installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;
