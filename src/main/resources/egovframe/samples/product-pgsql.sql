CREATE TABLE products(
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock_quantity INT DEFAULT 0,
  category VARCHAR(100),
  image_url VARCHAR(500),
  is_active BOOLEAN DEFAULT TRUE
);

COMMENT ON TABLE products IS 'Product Table';
COMMENT ON COLUMN products.id IS 'Product ID';
COMMENT ON COLUMN products.name IS 'Name';
COMMENT ON COLUMN products.description IS 'Description';
COMMENT ON COLUMN products.price IS 'Price';
COMMENT ON COLUMN products.stock_quantity IS 'Stock Quantity';
COMMENT ON COLUMN products.category IS 'Category';
COMMENT ON COLUMN products.image_url IS 'Image URL';
COMMENT ON COLUMN products.is_active IS 'Sale Status';
