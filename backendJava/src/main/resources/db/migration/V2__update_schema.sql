-- Update categories table schema
ALTER TABLE categories ADD COLUMN description TEXT;

-- Update products table schema
ALTER TABLE products ADD COLUMN color VARCHAR(50);
