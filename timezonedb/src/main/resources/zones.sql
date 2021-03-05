select  z.country_code, z.zone_name from zone z where zone_name like '%%%1$s%%'
order by z.country_code, z.zone_name;
