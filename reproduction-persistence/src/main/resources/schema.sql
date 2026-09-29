-- MySQL 8. Loaded by docker-compose (docker-entrypoint-initdb.d) and by the Testcontainers MySQL test.
CREATE TABLE IF NOT EXISTS reproduction_job (
  id                 CHAR(36)     NOT NULL,
  name               VARCHAR(200) NOT NULL,
  status             VARCHAR(20)  NOT NULL,
  strategy           VARCHAR(30)  NOT NULL,
  scenario_id        VARCHAR(100) NULL,
  idempotency_key    VARCHAR(200) NULL,
  request_fingerprint CHAR(64)    NOT NULL,
  initial_input_json MEDIUMTEXT   NOT NULL,
  options_json       TEXT         NOT NULL,
  original_field_count INT        NOT NULL DEFAULT 0,
  result_json        MEDIUMTEXT   NULL,
  error_message      VARCHAR(1000) NULL,
  created_at         DATETIME(3)  NOT NULL,
  updated_at         DATETIME(3)  NOT NULL,
  started_at         DATETIME(3)  NULL,
  completed_at       DATETIME(3)  NULL,
  deadline_at        DATETIME(3)  NULL,
  version            BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_job_idempotency (idempotency_key),
  KEY ix_job_status_created (status, created_at),
  KEY ix_job_deadline (status, deadline_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bug_signature (
  job_id CHAR(36) NOT NULL, http_status INT NULL, error_code VARCHAR(200) NULL, body_pattern VARCHAR(500) NULL,
  min_latency_ms BIGINT NULL, exception_signature VARCHAR(500) NULL,
  PRIMARY KEY (job_id), CONSTRAINT fk_sig_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS input_field (
  job_id CHAR(36) NOT NULL, idx INT NOT NULL, path VARCHAR(255) NOT NULL, value_json TEXT NOT NULL, payload_size INT NOT NULL,
  PRIMARY KEY (job_id, idx), UNIQUE KEY uq_field_path (job_id, path),
  CONSTRAINT fk_field_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS input_dependency (
  id BIGINT NOT NULL AUTO_INCREMENT, job_id CHAR(36) NOT NULL, from_idx INT NOT NULL, to_idx INT NOT NULL, reason VARCHAR(300) NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_dep (job_id, from_idx, to_idx),
  CONSTRAINT fk_dep_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS candidate (
  id BIGINT NOT NULL AUTO_INCREMENT, job_id CHAR(36) NOT NULL, candidate_hash CHAR(64) NOT NULL,
  field_count INT NOT NULL, canonical_text MEDIUMTEXT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_candidate_job_hash (job_id, candidate_hash), KEY ix_candidate_hash (candidate_hash),
  CONSTRAINT fk_cand_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS candidate_field (
  candidate_id BIGINT NOT NULL, field_idx INT NOT NULL,
  PRIMARY KEY (candidate_id, field_idx), KEY ix_cf_field (field_idx),
  CONSTRAINT fk_cf_cand FOREIGN KEY (candidate_id) REFERENCES candidate(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evaluation_result (
  id BIGINT NOT NULL AUTO_INCREMENT, job_id CHAR(36) NOT NULL, candidate_id BIGINT NOT NULL, task_id CHAR(36) NOT NULL,
  attempt INT NOT NULL, status VARCHAR(30) NOT NULL, attempts_run INT NOT NULL, reproductions INT NOT NULL,
  reproduction_rate DOUBLE NOT NULL, duration_ms BIGINT NOT NULL, source VARCHAR(30) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_eval (job_id, candidate_id, task_id, attempt),
  KEY ix_eval_job_created (job_id, created_at), KEY ix_eval_job_status (job_id, status),
  CONSTRAINT fk_eval_cand FOREIGN KEY (candidate_id) REFERENCES candidate(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reduction_step (
  id BIGINT NOT NULL AUTO_INCREMENT, job_id CHAR(36) NOT NULL, task_id CHAR(36) NOT NULL, step_index INT NOT NULL,
  phase VARCHAR(40) NOT NULL, action VARCHAR(40) NOT NULL, size_before INT NOT NULL, size_after INT NOT NULL,
  granularity INT NOT NULL, candidate_hash CHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_step (job_id, task_id, step_index), KEY ix_step_job (job_id, id),
  CONSTRAINT fk_step_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS worker_task (
  task_id CHAR(36) NOT NULL, job_id CHAR(36) NOT NULL, type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
  attempt INT NOT NULL, banned_json TEXT NOT NULL, result_json MEDIUMTEXT NULL, worker_id VARCHAR(100) NULL,
  lease_expires_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (task_id), KEY ix_task_job_status (job_id, status), KEY ix_task_status_lease (status, lease_expires_at),
  CONSTRAINT fk_task_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS job_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT, job_id CHAR(36) NOT NULL, attempt_no INT NOT NULL, trigger_type VARCHAR(20) NOT NULL,
  started_at DATETIME(3) NOT NULL, ended_at DATETIME(3) NULL, outcome VARCHAR(30) NULL,
  PRIMARY KEY (id), UNIQUE KEY uq_attempt (job_id, attempt_no),
  CONSTRAINT fk_attempt_job FOREIGN KEY (job_id) REFERENCES reproduction_job(id) ON DELETE CASCADE
) ENGINE=InnoDB;
