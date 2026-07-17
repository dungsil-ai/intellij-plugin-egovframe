CREATE TABLE orders(
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'shipped', 'delivered', 'cancelled')),
  shipping_address TEXT,
  payment_method VARCHAR(50),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

COMMENT ON TABLE orders IS 'Order Table';
COMMENT ON COLUMN orders.id IS 'Order ID';
COMMENT ON COLUMN orders.user_id IS 'User ID';
COMMENT ON COLUMN orders.order_date IS 'Order Date';
COMMENT ON COLUMN orders.total_amount IS 'Total Amount';
COMMENT ON COLUMN orders.status IS 'Order Status';
COMMENT ON COLUMN orders.shipping_address IS 'Shipping Address';
COMMENT ON COLUMN orders.payment_method IS 'Payment Method';
