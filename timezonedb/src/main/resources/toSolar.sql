
SELECT
       DATEADD('minute', DATEDIFF('minute', DATE '1970-01-01', TIMESTAMP '%1$s') - (((tz.gmt_offset / 3600 * 15 ) - cb.longitude ) * 4), DATE '1970-01-01') AS solar_time
FROM zone z JOIN citybase cb ON cb.TZ=z.ZONE_NAME
            JOIN timezone tz ON tz.zone_id=z.zone_id
WHERE tz.time_start <= DATEDIFF('second', DATE '1970-01-01', TIMESTAMP '%1$s')
 AND cb.name = '%2$s'
ORDER BY tz.time_start DESC LIMIT 1

