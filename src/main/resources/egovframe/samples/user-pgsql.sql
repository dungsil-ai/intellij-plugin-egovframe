CREATE TABLE users(
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(100),
  phone VARCHAR(20),
  is_active BOOLEAN DEFAULT TRUE
);

COMMENT ON TABLE users IS 'User Table';
COMMENT ON COLUMN users.id IS 'User ID';
COMMENT ON COLUMN users.username IS 'Username';
COMMENT ON COLUMN users.email IS 'Email';
COMMENT ON COLUMN users.password IS 'Password';
COMMENT ON COLUMN users.full_name IS 'Full Name';
COMMENT ON COLUMN users.phone IS 'Phone';
COMMENT ON COLUMN users.is_active IS 'Active';
