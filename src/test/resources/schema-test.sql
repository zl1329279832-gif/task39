-- H2 MySQL-compatible test schema

CREATE TABLE IF NOT EXISTS Admin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(50) NOT NULL,
    token VARCHAR(255),
    avatar VARCHAR(255) DEFAULT 'https://img1.baidu.com/default.jpg',
    create_time DATETIME,
    role VARCHAR(20) DEFAULT 'guest'
);

CREATE TABLE IF NOT EXISTS graphql_employee (
    id INT NOT NULL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    salary DECIMAL(10,2),
    ssn VARCHAR(20),
    internal_notes TEXT
);

CREATE TABLE IF NOT EXISTS MessageBoard (
    id INT AUTO_INCREMENT PRIMARY KEY,
    message VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS User (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    name VARCHAR(50),
    password VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS mfa_secret (
    id INT AUTO_INCREMENT PRIMARY KEY,
    userId INT NOT NULL,
    secret VARCHAR(100) NOT NULL,
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE IF NOT EXISTS sms_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(6) NOT NULL,
    create_time DATETIME NOT NULL,
    expire_time DATETIME NOT NULL,
    used INT DEFAULT 0,
    retry_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_login_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(50) NOT NULL,
    username VARCHAR(255) NOT NULL,
    loginTime DATETIME NOT NULL,
    loginResult VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS articles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Drill System Tables
CREATE TABLE IF NOT EXISTS drill_task (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    difficulty VARCHAR(20) DEFAULT 'medium',
    creator_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'active',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS drill_checkpoint (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    vuln_category VARCHAR(50) NOT NULL,
    checkpoint_order INT NOT NULL DEFAULT 0,
    mode VARCHAR(20) NOT NULL,
    vuln_endpoint VARCHAR(500) NOT NULL,
    sec_endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL DEFAULT 'GET',
    target_param VARCHAR(100),
    max_score INT NOT NULL DEFAULT 100,
    max_hints INT NOT NULL DEFAULT 3,
    time_limit INT DEFAULT 1800,
    prerequisite_id INT DEFAULT NULL,
    verify_pattern VARCHAR(1000),
    defense_pattern VARCHAR(1000),
    hint_content TEXT,
    version INT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS drill_attempt (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    checkpoint_id INT NOT NULL,
    user_id INT NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    attempt_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_summary TEXT,
    evidence TEXT,
    elapsed_seconds INT DEFAULT 0,
    server_elapsed_seconds INT DEFAULT 0,
    hints_used INT DEFAULT 0,
    deduction_items VARCHAR(1000),
    score INT DEFAULT 0,
    passed TINYINT DEFAULT 0,
    checkpoint_version INT NOT NULL DEFAULT 1,
    checkpoint_mode VARCHAR(20) NOT NULL,
    max_score_snapshot INT NOT NULL DEFAULT 100,
    max_hints_snapshot INT NOT NULL DEFAULT 3,
    time_limit_snapshot INT DEFAULT 1800,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_checkpoint_task ON drill_attempt(user_id, checkpoint_id, task_id);

CREATE TABLE IF NOT EXISTS drill_score_summary (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    user_id INT NOT NULL,
    total_score INT DEFAULT 0,
    max_possible INT DEFAULT 0,
    checkpoints_passed INT DEFAULT 0,
    checkpoints_total INT DEFAULT 0,
    completion_pct DECIMAL(5,2) DEFAULT 0,
    last_attempt_time DATETIME,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_user ON drill_score_summary(task_id, user_id);

-- Enhanced Drill System Tables
CREATE TABLE IF NOT EXISTS drill_task_instance (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    user_id INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    rules_snapshot TEXT,
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline DATETIME,
    total_score INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_instance_task_user ON drill_task_instance(task_id, user_id);

CREATE TABLE IF NOT EXISTS evidence_record (
    id INT AUTO_INCREMENT PRIMARY KEY,
    instance_id INT NOT NULL,
    task_id INT NOT NULL,
    checkpoint_id INT NOT NULL,
    user_id INT NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    auto_judgment VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_judgment VARCHAR(20) DEFAULT NULL,
    review_id INT DEFAULT NULL,
    submission_hash VARCHAR(64) NOT NULL,
    elapsed_seconds INT DEFAULT 0,
    hints_used INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_evidence_hash ON evidence_record(instance_id, checkpoint_id, submission_hash);

CREATE TABLE IF NOT EXISTS score_detail (
    id INT AUTO_INCREMENT PRIMARY KEY,
    instance_id INT NOT NULL,
    task_id INT NOT NULL,
    checkpoint_id INT NOT NULL,
    user_id INT NOT NULL,
    base_score INT NOT NULL DEFAULT 0,
    hint_deduction INT NOT NULL DEFAULT 0,
    time_deduction INT NOT NULL DEFAULT 0,
    retry_deduction INT NOT NULL DEFAULT 0,
    review_adjustment INT NOT NULL DEFAULT 0,
    final_score INT NOT NULL DEFAULT 0,
    passed TINYINT NOT NULL DEFAULT 0,
    snapshot_json TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sd_instance_checkpoint ON score_detail(instance_id, checkpoint_id);

CREATE TABLE IF NOT EXISTS review_record (
    id INT AUTO_INCREMENT PRIMARY KEY,
    evidence_id INT NOT NULL,
    instance_id INT NOT NULL,
    task_id INT NOT NULL,
    checkpoint_id INT NOT NULL,
    reviewer_id INT NOT NULL,
    original_judgment VARCHAR(20) NOT NULL,
    new_judgment VARCHAR(20) NOT NULL,
    reason TEXT,
    score_adjustment INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    actor_id INT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id INT NOT NULL,
    detail TEXT,
    ip_address VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
