CREATE TABLE board(
  id VARCHAR(36) PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  content TEXT,
  author VARCHAR(100) NOT NULL,
  view_count INT DEFAULT 0
);

COMMENT ON TABLE board IS 'Board Table';
COMMENT ON COLUMN board.id IS 'Board ID';
COMMENT ON COLUMN board.title IS 'Title';
COMMENT ON COLUMN board.content IS 'Content';
COMMENT ON COLUMN board.author IS 'Author';
COMMENT ON COLUMN board.view_count IS 'View Count';
