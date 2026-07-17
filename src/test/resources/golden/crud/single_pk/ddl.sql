CREATE TABLE board_article
    (
      article_id  INT PRIMARY KEY,
      title       VARCHAR(255) NOT NULL COMMENT '제목',
      content     TEXT COMMENT '본문 내용',
      view_count  INT     DEFAULT 0,
      created_at  DATETIME     NOT NULL COMMENT '작성일시',
      price       DECIMAL(10, 2) COMMENT '가격',
      use_yn      CHAR(1) DEFAULT 'Y' COMMENT '사용여부',
      writer_name VARCHAR(255)
    ) COMMENT='게시판';
