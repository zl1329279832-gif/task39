-- 设置数据库字符集
ALTER DATABASE SpringVulnBoot CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 创建管理员表
create table if not exists Admin
(
    id          int auto_increment
        primary key,
    name        varchar(50)                                                                                                         not null,
    username    varchar(50)                                                                                                         not null,
    password    varchar(50)                                                                                                         not null,
    token       varchar(255)                                                                                                        null,
    avatar      varchar(255) default 'https://img1.baidu.com/it/u=3200425930,2413475553&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800' null,
    create_time datetime                                                                                                            null,
    role        varchar(20)  default 'guest'                                                                                        null comment '角色：admin-管理员, guest-访客',
    constraint username
        unique (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 插入测试数据到Admin表（id 固定，供 GraphQL 演示使用）
INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (1, '系统管理员', 'admin',    '123456',   CONCAT('token_', ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000)), 'admin', NOW());
INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (2, '审计员',   'zhangsan', '123456',   CONCAT('token_', ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000)), 'admin', NOW());
INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (3, '访客用户', 'guest',    'guest123', CONCAT('token_', ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000)), 'guest', NOW());

-- 创建员工信息表（GraphQL 漏洞演示数据源）
-- id 与 Admin.id 一一对应，GraphQL 查询时 JOIN 两表获取完整信息
create table if not exists graphql_employee
(
    id             int           not null primary key comment '对应 Admin 用户 ID',
    email          varchar(100)  not null comment '邮箱',
    salary         decimal(10,2) null comment '薪资（敏感字段）',
    ssn            varchar(20)   null comment '社保号（敏感字段）',
    internal_notes text          null comment '内部备注（敏感字段）',
    constraint fk_graphql_employee_admin foreign key (id) references Admin (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工信息表（GraphQL 漏洞演示数据源）';

-- 插入员工数据（对应 Admin 表的 admin / zhangsan / guest）
INSERT INTO graphql_employee (id, email, salary, ssn, internal_notes) VALUES
(1, 'admin@company.com',    150000.00, '123-45-6789', '系统管理员，拥有最高权限'),
(2, 'zhangsan@company.com',  80000.00, '234-56-7890', '审计员，负责系统日志审计'),
(3, 'guest@company.com',     40000.00, '345-67-8901', '访客账号，权限受限');

-- 创建留言表
create table if not exists MessageBoard
(
    id      int auto_increment
        primary key,
    message varchar(200) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 创建用户表
create table if not exists User
(
    id       int auto_increment
        primary key,
    username varchar(50) null,
    name     varchar(50) null,
    password varchar(50) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 创建MFA密钥表
create table if not exists mfa_secret
(
    id          int auto_increment
        primary key,
    userId      int          not null comment '用户ID',
    secret      varchar(100) not null comment 'MFA加密串',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    constraint mfa_secret_Admin_id_fk
        foreign key (userId) references Admin (id)
)
    comment 'MFA密钥表' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

create index userId
    on mfa_secret (userId);

-- 创建短信验证码记录表
create table if not exists sms_code
(
    id          bigint auto_increment
        primary key,
    phone       varchar(20)   not null comment '手机号',
    code        varchar(6)    not null comment '验证码',
    create_time datetime      not null comment '创建时间',
    expire_time datetime      not null comment '过期时间',
    used        int default 0 null comment '是否已使用：0未使用，1已使用',
    retry_count int default 0 null comment '验证重试次数'
)
    comment '短信验证码记录表' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 创建用户登录日志表
create table if not exists user_login_log
(
    id          int auto_increment
        primary key,
    ip          varchar(50)  not null,
    username    varchar(255) not null,
    loginTime   datetime     not null,
    loginResult varchar(10)  not null comment '登录结果：成功/失败'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 插入测试数据到MessageBoard表
INSERT INTO MessageBoard (id, message) VALUES (1, '这个靶场真棒！');
INSERT INTO MessageBoard (id, message) VALUES (2, '怎么没有命令执行漏洞系列？');
INSERT INTO MessageBoard (id, message) VALUES (3, '催更！！！');
INSERT INTO MessageBoard (id, message) VALUES (4, '<img src=x onmouseover=alert(/xss/)>');

-- 插入测试数据到User表
INSERT INTO User (id, username, name, password) VALUES (1, 'zhangsan', '张三', '123');
INSERT INTO User (id, username, name, password) VALUES (2, 'lisi', '李四', '123');
INSERT INTO User (id, username, name, password) VALUES (3, 'wangwu', '王五', '123');
INSERT INTO User (id, username, name, password) VALUES (4, 'zhaoliu', '赵六', '123');
INSERT INTO User (id, username, name, password) VALUES (5, 'qiaofeng', '乔峰', '123');
INSERT INTO User (id, username, name, password) VALUES (6, 'duanyu', '段誉', '123');
INSERT INTO User (id, username, name, password) VALUES (7, 'xuzhu', '虚竹', '123');
INSERT INTO User (id, username, name, password) VALUES (8, 'murongfu', '慕容复', '123');
INSERT INTO User (id, username, name, password) VALUES (9, 'duanzhengchun', '段正淳', '123');
INSERT INTO User (id, username, name, password) VALUES (10, 'saodiseng', '扫地僧', '123');
INSERT INTO User (id, username, name, password) VALUES (11, 'wangyuyan', '王语嫣', '123');
INSERT INTO User (id, username, name, password) VALUES (12, 'jiumozhi', '鸠摩智', '123');

-- 创建文章表（用于 UNION 注入演示）
CREATE TABLE IF NOT EXISTS articles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    author VARCHAR(50) NOT NULL COMMENT '作者',
    content TEXT NOT NULL COMMENT '文章内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章表';

-- 插入测试数据到 articles 表
INSERT INTO articles (title, author, content) VALUES 
('Spring Boot 入门教程', 'admin', 'Spring Boot 是一个基于 Spring 框架的快速开发框架，它简化了 Spring 应用的初始搭建以及开发过程。本教程将带你从零开始学习 Spring Boot 的核心特性和使用方法。'),
('MySQL 性能优化指南', 'zhangsan', 'MySQL 性能优化是数据库运维的核心工作之一。本文将介绍索引优化、查询优化、配置优化等关键技术，帮助你提升数据库性能。'),
('Java 安全编码规范', 'admin', '安全编码是软件开发中的重要环节。本文总结了 Java 开发中常见的安全漏洞类型，包括 SQL 注入、XSS、CSRF 等，并提供了相应的防御措施。'),
('Docker 容器化实战', 'lisi', 'Docker 是当前最流行的容器化技术。本文将介绍 Docker 的基本概念、常用命令，以及如何使用 Docker Compose 编排多容器应用。'),
('Vue.js 前端开发实践', 'zhangsan', 'Vue.js 是一套用于构建用户界面的渐进式 JavaScript 框架。本教程将通过实际案例，讲解 Vue 的核心概念、组件开发和状态管理。');

-- =============================================
-- Drill System Tables (演练任务编排与评分系统)
-- =============================================

-- 演练任务表
CREATE TABLE IF NOT EXISTS drill_task (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    difficulty  VARCHAR(20) DEFAULT 'medium' COMMENT '难度: easy, medium, hard',
    creator_id  INT NOT NULL COMMENT '创建者ID (Admin.id)',
    status      VARCHAR(20) DEFAULT 'active' COMMENT '状态: active, archived',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_drill_task_creator FOREIGN KEY (creator_id) REFERENCES Admin(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演练任务表';

-- 演练检查点表
CREATE TABLE IF NOT EXISTS drill_checkpoint (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    task_id          INT NOT NULL,
    vuln_category    VARCHAR(50) NOT NULL COMMENT '漏洞类别: SQLI_NUMERIC, XXE_BASIC, SSRF_BASIC, JWT_WEAK, DESERIALIZE, PATH_TRAVERSAL, ACCESS_CONTROL, MFA_BYPASS 等',
    checkpoint_order INT NOT NULL DEFAULT 0,
    mode             VARCHAR(20) NOT NULL COMMENT 'EXPLOIT 或 DEFENSE',
    vuln_endpoint    VARCHAR(500) NOT NULL COMMENT '漏洞端点路径',
    sec_endpoint     VARCHAR(500) NOT NULL COMMENT '安全端点路径',
    http_method      VARCHAR(10) NOT NULL DEFAULT 'GET',
    target_param     VARCHAR(100) COMMENT '目标参数名(GET请求)',
    max_score        INT NOT NULL DEFAULT 100,
    max_hints        INT NOT NULL DEFAULT 3,
    time_limit       INT DEFAULT 1800 COMMENT '时间限制(秒)',
    prerequisite_id  INT DEFAULT NULL,
    verify_pattern   VARCHAR(1000) COMMENT '验证正则(EXPLOIT模式)',
    defense_pattern  VARCHAR(1000) COMMENT '防御验证正则(DEFENSE模式)',
    hint_content     TEXT COMMENT '提示内容(JSON数组)',
    version          INT NOT NULL DEFAULT 1 COMMENT '检查点版本号，管理员修改时自增',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkpoint_task FOREIGN KEY (task_id) REFERENCES drill_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkpoint_prereq FOREIGN KEY (prerequisite_id) REFERENCES drill_checkpoint(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演练检查点表';

-- 演练尝试记录表
CREATE TABLE IF NOT EXISTS drill_attempt (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    task_id              INT NOT NULL,
    checkpoint_id        INT NOT NULL,
    user_id              INT NOT NULL COMMENT '学生ID (Admin.id)',
    attempt_number       INT NOT NULL DEFAULT 1 COMMENT '尝试序号',
    attempt_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_summary      TEXT COMMENT '攻击载荷摘要',
    evidence             TEXT COMMENT '成功证据',
    elapsed_seconds      INT DEFAULT 0 COMMENT '客户端报告的耗时',
    server_elapsed_seconds INT DEFAULT 0 COMMENT '服务端计算的耗时',
    hints_used           INT DEFAULT 0,
    deduction_items      VARCHAR(1000) COMMENT '扣分项JSON: [{reason, points}]',
    score                INT DEFAULT 0,
    passed               TINYINT DEFAULT 0 COMMENT '0=未通过, 1=通过',
    checkpoint_version   INT NOT NULL DEFAULT 1 COMMENT '提交时的检查点版本',
    checkpoint_mode      VARCHAR(20) NOT NULL COMMENT '提交时的检查点模式',
    max_score_snapshot   INT NOT NULL DEFAULT 100 COMMENT '提交时的满分快照',
    max_hints_snapshot   INT NOT NULL DEFAULT 3 COMMENT '提交时的最大提示次数快照',
    time_limit_snapshot  INT DEFAULT 1800 COMMENT '提交时的时间限制快照',
    create_time          DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_task FOREIGN KEY (task_id) REFERENCES drill_task(id),
    CONSTRAINT fk_attempt_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES drill_checkpoint(id),
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES Admin(id),
    CONSTRAINT uk_user_checkpoint_task UNIQUE (user_id, checkpoint_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演练尝试记录表';

-- 演练成绩统计表
CREATE TABLE IF NOT EXISTS drill_score_summary (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    task_id           INT NOT NULL,
    user_id           INT NOT NULL,
    total_score       INT DEFAULT 0,
    max_possible      INT DEFAULT 0,
    checkpoints_passed INT DEFAULT 0,
    checkpoints_total  INT DEFAULT 0,
    completion_pct    DECIMAL(5,2) DEFAULT 0,
    last_attempt_time DATETIME,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_score_task FOREIGN KEY (task_id) REFERENCES drill_task(id),
    CONSTRAINT fk_score_user FOREIGN KEY (user_id) REFERENCES Admin(id),
    CONSTRAINT uk_task_user UNIQUE (task_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演练成绩统计表';

-- =============================================
-- 分层演练任务与证据复核系统 (Enhanced Drill System)
-- =============================================

-- 演练任务实例表（学员开始任务时创建，快照任务规则）
CREATE TABLE IF NOT EXISTS drill_task_instance (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    task_id       INT NOT NULL COMMENT '关联的任务定义',
    user_id       INT NOT NULL COMMENT '学员ID (Admin.id)',
    status        VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS, COMPLETED, EXPIRED',
    rules_snapshot TEXT COMMENT '任务规则快照JSON（检查点配置冻结）',
    start_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline      DATETIME COMMENT '截止时间',
    total_score   INT DEFAULT 0,
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_instance_task FOREIGN KEY (task_id) REFERENCES drill_task(id),
    CONSTRAINT fk_instance_user FOREIGN KEY (user_id) REFERENCES Admin(id),
    CONSTRAINT uk_instance_task_user UNIQUE (task_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演练任务实例表';

-- 证据记录表（学员提交的每条证据）
CREATE TABLE IF NOT EXISTS evidence_record (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    instance_id     INT NOT NULL COMMENT '任务实例ID',
    task_id         INT NOT NULL,
    checkpoint_id   INT NOT NULL,
    user_id         INT NOT NULL,
    evidence_type   VARCHAR(30) NOT NULL COMMENT 'PAYLOAD, SCREENSHOT_HASH, REQUEST_LOG',
    content         TEXT NOT NULL COMMENT '证据内容',
    mode            VARCHAR(20) NOT NULL COMMENT 'EXPLOIT 或 DEFENSE',
    auto_judgment   VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'HIT, MISS, PENDING',
    admin_judgment  VARCHAR(20) DEFAULT NULL COMMENT '管理员复核判定: HIT, MISS',
    review_id       INT DEFAULT NULL COMMENT '关联复核单ID',
    submission_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 防重复提交',
    elapsed_seconds INT DEFAULT 0,
    hints_used      INT DEFAULT 0,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_instance FOREIGN KEY (instance_id) REFERENCES drill_task_instance(id),
    CONSTRAINT fk_evidence_task FOREIGN KEY (task_id) REFERENCES drill_task(id),
    CONSTRAINT fk_evidence_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES drill_checkpoint(id),
    CONSTRAINT fk_evidence_user FOREIGN KEY (user_id) REFERENCES Admin(id),
    CONSTRAINT uk_evidence_hash UNIQUE (instance_id, checkpoint_id, submission_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证据记录表';

-- 评分明细表（每个检查点的评分分解）
CREATE TABLE IF NOT EXISTS score_detail (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    instance_id       INT NOT NULL,
    task_id           INT NOT NULL,
    checkpoint_id     INT NOT NULL,
    user_id           INT NOT NULL,
    base_score        INT NOT NULL DEFAULT 0,
    hint_deduction    INT NOT NULL DEFAULT 0,
    time_deduction    INT NOT NULL DEFAULT 0,
    retry_deduction   INT NOT NULL DEFAULT 0,
    review_adjustment INT NOT NULL DEFAULT 0 COMMENT '复核调分',
    final_score       INT NOT NULL DEFAULT 0,
    passed            TINYINT NOT NULL DEFAULT 0,
    snapshot_json     TEXT COMMENT '检查点配置快照',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sd_instance FOREIGN KEY (instance_id) REFERENCES drill_task_instance(id),
    CONSTRAINT fk_sd_task FOREIGN KEY (task_id) REFERENCES drill_task(id),
    CONSTRAINT fk_sd_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES drill_checkpoint(id),
    CONSTRAINT fk_sd_user FOREIGN KEY (user_id) REFERENCES Admin(id),
    CONSTRAINT uk_sd_instance_checkpoint UNIQUE (instance_id, checkpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评分明细表';

-- 复核单表（管理员对证据的复核记录）
CREATE TABLE IF NOT EXISTS review_record (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    evidence_id       INT NOT NULL COMMENT '被复核的证据ID',
    instance_id       INT NOT NULL,
    task_id           INT NOT NULL,
    checkpoint_id     INT NOT NULL,
    reviewer_id       INT NOT NULL COMMENT '复核管理员ID',
    original_judgment VARCHAR(20) NOT NULL COMMENT '原始判定',
    new_judgment      VARCHAR(20) NOT NULL COMMENT '新判定',
    reason            TEXT COMMENT '复核理由',
    score_adjustment  INT DEFAULT 0 COMMENT '分数调整量',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_evidence FOREIGN KEY (evidence_id) REFERENCES evidence_record(id),
    CONSTRAINT fk_review_instance FOREIGN KEY (instance_id) REFERENCES drill_task_instance(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES Admin(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复核单表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    action      VARCHAR(50) NOT NULL COMMENT '操作类型',
    actor_id    INT NOT NULL COMMENT '操作人ID',
    actor_role  VARCHAR(20) NOT NULL,
    target_type VARCHAR(30) NOT NULL COMMENT 'TASK, INSTANCE, EVIDENCE, REVIEW, CHECKPOINT',
    target_id   INT NOT NULL,
    detail      TEXT COMMENT '操作详情JSON',
    ip_address  VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES Admin(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';