PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT,
    chapter TEXT,
    page TEXT,
    file_path TEXT,
    url TEXT,
    notes TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS regions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name_bg TEXT NOT NULL,
    name_en TEXT,
    ontology_iri TEXT UNIQUE,
    description TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ornament_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name_bg TEXT NOT NULL,
    name_en TEXT NOT NULL,
    ontology_iri TEXT UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS ornaments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name_bg TEXT NOT NULL,
    name_en TEXT,
    category_id INTEGER NOT NULL,
    ontology_iri TEXT UNIQUE,
    description TEXT,
    source_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES ornament_categories(id),
    FOREIGN KEY (source_id) REFERENCES sources(id)
);

CREATE TABLE IF NOT EXISTS embroideries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ref_number TEXT NOT NULL UNIQUE,
    name_bg TEXT NOT NULL,
    name_en TEXT,
    region_id INTEGER,
    description TEXT,
    ontology_iri TEXT UNIQUE,
    source_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (region_id) REFERENCES regions(id),
    FOREIGN KEY (source_id) REFERENCES sources(id)
);

CREATE TABLE IF NOT EXISTS embroidery_ornaments (
    embroidery_id INTEGER NOT NULL,
    ornament_id INTEGER NOT NULL,
    confidence REAL NOT NULL DEFAULT 1.0 CHECK (confidence >= 0.0 AND confidence <= 1.0),
    notes TEXT,
    PRIMARY KEY (embroidery_id, ornament_id),
    FOREIGN KEY (embroidery_id) REFERENCES embroideries(id) ON DELETE CASCADE,
    FOREIGN KEY (ornament_id) REFERENCES ornaments(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS colors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name_bg TEXT NOT NULL,
    name_en TEXT,
    hex_code TEXT,
    ontology_iri TEXT UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS embroidery_colors (
    embroidery_id INTEGER NOT NULL,
    color_id INTEGER NOT NULL,
    is_dominant INTEGER NOT NULL DEFAULT 0 CHECK (is_dominant IN (0, 1)),
    notes TEXT,
    PRIMARY KEY (embroidery_id, color_id),
    FOREIGN KEY (embroidery_id) REFERENCES embroideries(id) ON DELETE CASCADE,
    FOREIGN KEY (color_id) REFERENCES colors(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS techniques (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name_bg TEXT NOT NULL,
    name_en TEXT,
    ontology_iri TEXT UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS embroidery_techniques (
    embroidery_id INTEGER NOT NULL,
    technique_id INTEGER NOT NULL,
    notes TEXT,
    PRIMARY KEY (embroidery_id, technique_id),
    FOREIGN KEY (embroidery_id) REFERENCES embroideries(id) ON DELETE CASCADE,
    FOREIGN KEY (technique_id) REFERENCES techniques(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS images (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    embroidery_id INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    mime_type TEXT,
    width INTEGER CHECK (width IS NULL OR width > 0),
    height INTEGER CHECK (height IS NULL OR height > 0),
    caption TEXT,
    is_primary INTEGER NOT NULL DEFAULT 0 CHECK (is_primary IN (0, 1)),
    source_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (embroidery_id) REFERENCES embroideries(id) ON DELETE CASCADE,
    FOREIGN KEY (source_id) REFERENCES sources(id)
);

CREATE INDEX IF NOT EXISTS idx_regions_name_bg ON regions(name_bg);
CREATE INDEX IF NOT EXISTS idx_embroideries_region_id ON embroideries(region_id);
CREATE INDEX IF NOT EXISTS idx_embroideries_source_id ON embroideries(source_id);
CREATE INDEX IF NOT EXISTS idx_ornaments_category_id ON ornaments(category_id);
CREATE INDEX IF NOT EXISTS idx_ornaments_source_id ON ornaments(source_id);
CREATE INDEX IF NOT EXISTS idx_images_embroidery_id ON images(embroidery_id);
CREATE INDEX IF NOT EXISTS idx_images_source_id ON images(source_id);
