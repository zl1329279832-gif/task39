-- Test seed data

INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (1, '系统管理员', 'admin', '123456', 'token_admin', 'admin', NOW());
INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (2, '审计员', 'zhangsan', '123456', 'token_zhangsan', 'admin', NOW());
INSERT INTO Admin (id, name, username, password, token, role, create_time) VALUES (3, '访客用户', 'guest', 'guest123', 'token_guest', 'guest', NOW());

INSERT INTO User (id, username, name, password) VALUES (1, 'zhangsan', '张三', '123');
INSERT INTO User (id, username, name, password) VALUES (2, 'lisi', '李四', '123');
INSERT INTO User (id, username, name, password) VALUES (3, 'wangwu', '王五', '123');

INSERT INTO articles (title, author, content) VALUES ('Spring Boot 入门', 'admin', 'Spring Boot 教程内容');
INSERT INTO articles (title, author, content) VALUES ('MySQL 优化', 'zhangsan', 'MySQL 优化内容');
