-- Tìm kiếm sản phẩm không dấu: unaccent + pg_trgm
CREATE EXTENSION IF NOT EXISTS pg_trgm  WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;

-- unaccent mặc định là STABLE -> cần bản IMMUTABLE để dùng trong cột sinh tự động / index
CREATE OR REPLACE FUNCTION immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

ALTER TABLE products
    ADD COLUMN search_name text
    GENERATED ALWAYS AS (lower(immutable_unaccent(name))) STORED;

CREATE INDEX idx_products_search_name_trgm
    ON products USING gin (search_name gin_trgm_ops);

CREATE INDEX idx_products_category_id ON products (category_id);
