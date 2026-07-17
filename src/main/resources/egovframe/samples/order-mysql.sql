CREATE TABLE orders(
  id VARCHAR(36) PRIMARY KEY COMMENT 'Order ID',
  user_id VARCHAR(36) NOT NULL COMMENT 'User ID',
  order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Order Date',
  total_amount DECIMAL(10,2) NOT NULL COMMENT 'Total Amount',
  status ENUM('pending', 'paid', 'shipped', 'delivered', 'cancelled') DEFAULT 'pending' COMMENT 'Order Status',
  shipping_address TEXT COMMENT 'Shipping Address',
  payment_method VARCHAR(50) COMMENT 'Payment Method',
  FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT 'Order Table';
