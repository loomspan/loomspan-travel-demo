CREATE TABLE catalog_receipt (
  assessment_id VARCHAR(40) NOT NULL REFERENCES assessment(id),
  kind VARCHAR(20) NOT NULL,
  payload CLOB NOT NULL,
  PRIMARY KEY (assessment_id,kind)
);
