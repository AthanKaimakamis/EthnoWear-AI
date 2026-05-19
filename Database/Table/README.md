# EthnoWear SQLite Tables

This folder contains plain SQLite scripts for the EthnoWear archive database.

## Create the database

From the `FinalProject/EthnoWear` directory:

```bash
sqlite3 Database/ethnowear.sqlite < Database/Table/001_create_schema.sql
sqlite3 Database/ethnowear.sqlite < Database/Table/002_seed_initial_data.sql
```

Or from the `FinalProject` directory:

```bash
sqlite3 EthnoWear/Database/ethnowear.sqlite < EthnoWear/Database/Table/001_create_schema.sql
sqlite3 EthnoWear/Database/ethnowear.sqlite < EthnoWear/Database/Table/002_seed_initial_data.sql
```

## Check the result

```bash
sqlite3 Database/ethnowear.sqlite ".tables"
sqlite3 Database/ethnowear.sqlite "SELECT ref_number, name_bg FROM embroideries;"
```

## Design notes

- `embroideries` is the main archive table for шевици.
- `regions`, `ornaments`, `colors`, and `techniques` mirror the main ontology concepts.
- Many-to-many tables connect one embroidery to multiple ornaments, colors, and techniques.
- `ontology_iri` keeps rows linkable to `Ontology/EthnoWear.owx`.
- `images` stores file paths and metadata, not base64. Put image files under a folder such as `Database/images/EP001/`.
- `sources` tracks literature references such as Chapter IV, so archive facts can be explained later.
