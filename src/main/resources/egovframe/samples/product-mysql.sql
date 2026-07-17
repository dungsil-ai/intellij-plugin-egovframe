CREATE TABLE products(
  id VARCHAR(36) PRIMARY KEY COMMENT 'Product ID',
  name VARCHAR(200) NOT NULL COMMENT 'Name',
  description TEXT COMMENT 'Description',
  price DECIMAL(10,2) NOT NULL COMMENT 'Price',
  stock_quantity INT DEFAULT 0 COMMENT 'Stock Quantity',
  category VARCHAR(100) COMMENT 'Category',
  image_url VARCHAR(500) COMMENT 'Image URL',
  is_active BOOLEAN DEFAULT TRUE COMMENT 'Sale Status'
) COMMENT 'Product Table';
