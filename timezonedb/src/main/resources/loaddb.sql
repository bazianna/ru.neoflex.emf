DROP TABLE IF EXISTS zone;
CREATE TABLE zone
(
    zone_id      INT(10)     NOT NULL,
    country_code CHAR(2)     NOT NULL,
    zone_name    VARCHAR(35) NOT NULL,
    PRIMARY KEY (zone_id)
)
AS
SELECT *
FROM CSVREAD('%1$s/zone.csv');
CREATE INDEX idx_zone_country_code ON zone (country_code);
CREATE INDEX idx_zone_zone_name ON zone (zone_name);
DROP TABLE IF EXISTS timezone;
CREATE TABLE timezone
(
    zone_id      INT(10)        NOT NULL,
    abbreviation VARCHAR(6)     NOT NULL,
    time_start   DECIMAL(11, 0) NOT NULL,
    gmt_offset   INT            NOT NULL,
    dst          CHAR(1)        NOT NULL
)
AS
SELECT *
FROM CSVREAD('%1$s/timezone.csv');
CREATE INDEX idx_timezone_zone_id ON timezone (zone_id);
CREATE INDEX idx_timezone_time_start ON timezone (time_start);
DROP TABLE IF EXISTS country;
CREATE TABLE country
(
    country_code CHAR(2)     NULL,
    country_name VARCHAR(45) NULL
)AS
SELECT *
FROM CSVREAD('%1$s/country.csv');
CREATE INDEX idx_country_country_code ON country (country_code);
