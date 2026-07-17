CREATE TABLE comments(
  id VARCHAR(36) PRIMARY KEY,
  board_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  content TEXT NOT NULL,
  parent_id VARCHAR(36),
  FOREIGN KEY (board_id) REFERENCES board(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (parent_id) REFERENCES comments(id)
);

COMMENT ON TABLE comments IS 'Comment Table';
COMMENT ON COLUMN comments.id IS 'Comment ID';
COMMENT ON COLUMN comments.board_id IS 'Board ID';
COMMENT ON COLUMN comments.user_id IS 'User ID';
COMMENT ON COLUMN comments.content IS 'Content';
COMMENT ON COLUMN comments.parent_id IS 'Parent Comment ID';
