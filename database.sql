CREATE TABLE suppliers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    contact TEXT,
    location TEXT
);

CREATE TABLE products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    price REAL NOT NULL,
    quantity INTEGER NOT NULL,
    supplier_id INTEGER,
    location TEXT,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE bills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    datetime TEXT,
    customer TEXT,
    total REAL
);

CREATE TABLE bill_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id INTEGER,
    product_id INTEGER,
    name TEXT,
    price REAL,
    qty INTEGER,
    FOREIGN KEY (bill_id) REFERENCES bills(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
ALTER TABLE products ADD COLUMN purchase_price REAL DEFAULT 0;
ALTER TABLE products ADD COLUMN gst REAL DEFAULT 0;
ALTER TABLE products ADD COLUMN purchase_price REAL DEFAULT 0;
ALTER TABLE products ADD COLUMN gst REAL DEFAULT 0;
