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
    max_hint_count INT DEFAULT NULL,
    time_limit_minutes INT DEFAULT NULL,
    allow_retry TINYINT DEFAULT 1,
    evidence_review_required TINYINT DEFAULT 0,
    prerequisite_knowledge TEXT,
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
    screenshot_hash VARCHAR(64),
    request_log TEXT,
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

-- Review ticket table
CREATE TABLE IF NOT EXISTS drill_review_ticket (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    attempt_id        INT NOT NULL,
    task_id           INT NOT NULL,
    checkpoint_id     INT NOT NULL,
    user_id           INT NOT NULL,
    reviewer_id       INT,
    original_passed   TINYINT NOT NULL,
    original_score    INT NOT NULL,
    review_status     VARCHAR(20) NOT NULL DEFAULT 'pending',
    overridden_passed TINYINT,
    overridden_score  INT,
    reason            TEXT,
    review_comment    TEXT,
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    review_time       DATETIME
);

CREATE INDEX IF NOT EXISTS idx_review_ticket_attempt ON drill_review_ticket(attempt_id);
CREATE INDEX IF NOT EXISTS idx_review_ticket_task ON drill_review_ticket(task_id);
CREATE INDEX IF NOT EXISTS idx_review_ticket_status ON drill_review_ticket(review_status);

-- Audit log table
CREATE TABLE IF NOT EXISTS drill_audit_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    actor_id    INT NOT NULL,
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id   INT NOT NULL,
    details     TEXT,
    ip_address  VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_target ON drill_audit_log(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON drill_audit_log(actor_id);

-- Review rule table
CREATE TABLE IF NOT EXISTS drill_review_rule (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    task_id                INT NOT NULL,
    auto_approve_threshold INT DEFAULT 80,
    manual_review_below    INT DEFAULT 50,
    manual_review_triggers VARCHAR(500),
    create_time            DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time            DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_review_rule_task ON drill_review_rule(task_id);
