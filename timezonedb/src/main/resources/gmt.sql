SELECT z.zone_name, DATEADD('SECOND', DATEDIFF('SECOND', DATE '1970-01-01', TIMESTAMP '%1$s') - tz.gmt_offset, DATE '1970-01-01') AS local_time
FROM timezone tz JOIN zone z ON tz.zone_id=z.zone_id
WHERE tz.time_start <= DATEDIFF('SECOND', DATE '1970-01-01', TIMESTAMP '%1$s')
  AND z.zone_name = '%2$s'
ORDER BY tz.time_start DESC LIMIT 1;
