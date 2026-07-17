CREATE TABLE `event_info`
    (
      `event_id`    INT PRIMARY KEY COMMENT 'Event''s ID & <identifier>',
      `event_name`  VARCHAR(255) NOT NULL COMMENT 'Name with ''quotes'' & <angle> brackets',
      `description` TEXT COMMENT 'A & B < C'
    ) COMMENT='이벤트 정보';
