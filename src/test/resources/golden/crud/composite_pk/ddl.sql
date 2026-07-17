CREATE TABLE order_item
    (
      order_id     INT          NOT NULL,
      item_seq     INT          NOT NULL,
      product_name VARCHAR(255) NOT NULL COMMENT '상품명',
      quantity     INT DEFAULT 1,
      unit_price   DECIMAL(10, 2),
      addr_1       VARCHAR(255) COMMENT '주소1',
      addr_2       VARCHAR(255) COMMENT '주소2',
      PRIMARY KEY (order_id, item_seq)
    );
