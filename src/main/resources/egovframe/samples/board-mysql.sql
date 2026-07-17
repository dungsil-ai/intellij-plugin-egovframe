CREATE TABLE board(
  id VARCHAR(36) PRIMARY KEY COMMENT 'Board ID',
  title VARCHAR(200) NOT NULL COMMENT 'Title',
  content TEXT COMMENT 'Content',
  author VARCHAR(100) NOT NULL COMMENT 'Author',
  view_count INT DEFAULT 0 COMMENT 'View Count'
) COMMENT 'Board Table';
