CREATE TABLE comments(
  id VARCHAR(36) PRIMARY KEY COMMENT 'Comment ID',
  board_id VARCHAR(36) NOT NULL COMMENT 'Board ID',
  user_id VARCHAR(36) NOT NULL COMMENT 'User ID',
  content TEXT NOT NULL COMMENT 'Content',
  parent_id VARCHAR(36) COMMENT 'Parent Comment ID',
  FOREIGN KEY (board_id) REFERENCES board(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (parent_id) REFERENCES comments(id)
) COMMENT 'Comment Table';
