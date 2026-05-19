PRAGMA foreign_keys = ON;

INSERT OR IGNORE INTO sources (
    id,
    title,
    author,
    chapter,
    file_path,
    notes
) VALUES (
    1,
    'Българска везбена орнаментика',
    NULL,
    'Глава IV - Названия на везбените орнаменти',
    '../Resources/docs/Българка Везбена Орнаментика/ Глава-IV Названия на Везбените Орнаменти.pdf',
    'Local literature source. OCR is noisy; verify facts manually before treating them as final.'
)
ON CONFLICT(id) DO UPDATE SET
    title = excluded.title,
    author = excluded.author,
    chapter = excluded.chapter,
    page = excluded.page,
    file_path = excluded.file_path,
    url = excluded.url,
    notes = excluded.notes;

INSERT OR IGNORE INTO ornament_categories (code, name_bg, name_en, ontology_iri, description) VALUES
    ('geometric', 'Геометрични', 'Geometric', '#GeometricOrnament', 'Geometric embroidery ornaments.'),
    ('plant', 'Растителни', 'Plant', '#PlantOrnament', 'Plant embroidery ornaments.'),
    ('animal', 'Животински', 'Animal', '#AnimalOrnament', 'Animal embroidery ornaments.'),
    ('human', 'Човешки фигури', 'Human', '#HumanOrnament', 'Human figure embroidery ornaments.'),
    ('symbolic', 'Символични', 'Symbolic', '#SymbolicOrnament', 'Symbolic embroidery ornaments.'),
    ('unknown', 'Неуточнени', 'Unknown', NULL, 'Use until the ornament category is verified.');

INSERT OR IGNORE INTO regions (name_bg, name_en, ontology_iri, description) VALUES
    ('Шоплук', 'Shopluk', '#ShoplukRegion', 'Starter ethnographic region from the ontology context.'),
    ('Добруджа', 'Dobrudzha', '#DobrudzhaRegion', 'Starter ethnographic region from the ontology context.'),
    ('Самоков', 'Samokov', NULL, 'Starter archive region candidate.'),
    ('Пловдив', 'Plovdiv', NULL, 'Starter archive region candidate.');

INSERT OR IGNORE INTO colors (name_bg, name_en, hex_code, ontology_iri, description) VALUES
    ('Червен', 'Red', '#C62828', '#RedColor', 'Common embroidery color.'),
    ('Черен', 'Black', '#111111', '#BlackColor', 'Common embroidery color.'),
    ('Бял', 'White', '#FFFFFF', '#WhiteColor', 'Common embroidery color.');

INSERT OR IGNORE INTO ornaments (name_bg, name_en, category_id, ontology_iri, description, source_id)
SELECT 'Ромб', 'Rhombus', id, '#RhombusOrnament', 'Starter geometric ornament from the ontology context.', 1
FROM ornament_categories
WHERE code = 'geometric';

INSERT OR IGNORE INTO ornaments (name_bg, name_en, category_id, ontology_iri, description, source_id)
SELECT 'Кръст', 'Cross', id, '#CrossOrnament', 'Starter geometric ornament from the ontology context.', 1
FROM ornament_categories
WHERE code = 'geometric';

INSERT OR IGNORE INTO ornaments (name_bg, name_en, category_id, ontology_iri, description, source_id)
SELECT 'Триъгълник', 'Triangle', id, '#TriangleOrnament', 'Starter geometric ornament from the ontology context.', 1
FROM ornament_categories
WHERE code = 'geometric';

INSERT OR IGNORE INTO ornaments (name_bg, name_en, category_id, ontology_iri, description, source_id)
SELECT 'Цвете', 'Flower', id, '#FlowerOrnament', 'Starter plant ornament from the ontology context.', 1
FROM ornament_categories
WHERE code = 'plant';

INSERT OR IGNORE INTO ornaments (name_bg, name_en, category_id, ontology_iri, description, source_id)
SELECT 'Лоза', 'Vine', id, '#VineOrnament', 'Starter plant ornament from the ontology context.', 1
FROM ornament_categories
WHERE code = 'plant';

INSERT OR IGNORE INTO embroideries (
    ref_number,
    name_bg,
    name_en,
    region_id,
    description,
    ontology_iri,
    source_id
)
SELECT
    'EP001',
    'Примерна шевица 001',
    'Sample Embroidery 001',
    regions.id,
    'Prototype archive record linked to the Shopluk region.',
    '#SampleEmbroidery001',
    1
FROM regions
WHERE regions.ontology_iri = '#ShoplukRegion';

INSERT OR IGNORE INTO embroideries (
    ref_number,
    name_bg,
    name_en,
    region_id,
    description,
    ontology_iri,
    source_id
)
SELECT
    'EP002',
    'Самоковска шевица',
    'Samokov Embroidery',
    regions.id,
    'Starter archive record for later manual completion.',
    NULL,
    1
FROM regions
WHERE regions.name_en = 'Samokov';

INSERT OR IGNORE INTO embroidery_ornaments (embroidery_id, ornament_id, confidence, notes)
SELECT embroideries.id, ornaments.id, 1.0, 'Starter ontology-context relation.'
FROM embroideries, ornaments
WHERE embroideries.ref_number = 'EP001'
  AND ornaments.ontology_iri = '#RhombusOrnament';

INSERT OR IGNORE INTO embroidery_colors (embroidery_id, color_id, is_dominant, notes)
SELECT embroideries.id, colors.id, 1, 'Starter dominant color.'
FROM embroideries, colors
WHERE embroideries.ref_number = 'EP001'
  AND colors.ontology_iri = '#RedColor';

INSERT OR IGNORE INTO images (
    embroidery_id,
    file_path,
    file_name,
    mime_type,
    caption,
    is_primary,
    source_id
)
SELECT
    embroideries.id,
    'Database/images/EP001/primary.jpg',
    'primary.jpg',
    'image/jpeg',
    'Placeholder path for the primary EP001 archive image.',
    1,
    1
FROM embroideries
WHERE embroideries.ref_number = 'EP001';
