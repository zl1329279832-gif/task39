-- Test seed data (MERGE INTO for H2 idempotent re-execution)

MERGE INTO Admin (id, name, username, password, token, role, create_time) KEY(id) VALUES (1, '系统管理员', 'admin', '123456', 'token_admin', 'admin', NOW());
MERGE INTO Admin (id, name, username, password, token, role, create_time) KEY(id) VALUES (2, '审计员', 'zhangsan', '123456', 'token_zhangsan', 'admin', NOW());
MERGE INTO Admin (id, name, username, password, token, role, create_time) KEY(id) VALUES (3, '访客用户', 'guest', 'guest123', 'token_guest', 'guest', NOW());

MERGE INTO User (id, username, name, password) KEY(id) VALUES (1, 'zhangsan', '张三', '123');
MERGE INTO User (id, username, name, password) KEY(id) VALUES (2, 'lisi', '李四', '123');
MERGE INTO User (id, username, name, password) KEY(id) VALUES (3, 'wangwu', '王五', '123');

MERGE INTO articles (id, title, author, content) KEY(id) VALUES (1, 'Spring Boot 入门', 'admin', 'Spring Boot 教程内容');
MERGE INTO articles (id, title, author, content) KEY(id) VALUES (2, 'MySQL 优化', 'zhangsan', 'MySQL 优化内容');
