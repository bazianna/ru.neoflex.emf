
SELECT
    tz.gmt_offset / 3600,
       8 - tz.gmt_offset / 3600,
       DATEADD('minute', DATEDIFF('minute', DATE '1970-01-01', TIMESTAMP '1993-07-05 15:30:00') + ((tz.gmt_offset / 3600 * 15 ) - cb.longitude ) * 4, DATE '1970-01-01') AS solar_time
FROM zone z JOIN citybase cb ON cb.country_code=z.country_code AND cb.TZ=z.ZONE_NAME
            JOIN timezone tz ON tz.zone_id=z.zone_id
WHERE tz.time_start <= DATEDIFF('minute', DATE '1970-01-01', TIMESTAMP '1993-07-05 15:30:00')
  AND cb.name = 'Ижевск'
ORDER BY tz.time_start DESC LIMIT 1

