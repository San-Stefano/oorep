# --- !Ups

ALTER TABLE RUBRIC ALTER COLUMN FULLPATH TYPE TEXT;

# --- !Downs

DROP TABLE RUBRIC;
