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
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS drill_attempt (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    checkpoint_id INT NOT NULL,
    user_id INT NOT NULL,
    attempt_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_summary TEXT,
    evidence TEXT,
    elapsed_seconds INT DEFAULT 0,
    hints_used INT DEFAULT 0,
    deduction_items VARCHAR(500),
    mode VARCHAR(20) NOT NULL DEFAULT 'EXPLOIT',
    scored_max_score INT,
    scored_mode VARCHAR(20),
    score INT DEFAULT 0,
    passed TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_checkpoint ON drill_attempt(user_id, checkpoint_id, mode);

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
