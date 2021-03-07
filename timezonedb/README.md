Сервис обеспечивает приведение локального времени к GMT (или обратно).

Пример

```shell
curl -X GET "http://localhost:8080/timezonedb/timeShift?fromDT=1969-09-02%2012%3A30%3A00&fromZone=Europe%2FMoscow&toZone=Asia%2FShanghai" -H "accept: */*"
```

```json
[
  {
    "gmtDT": "1969-09-02 09:30:00",
    "timeZone": "Europe/Moscow",
    "abbreviation": "MSK",
    "gmtOffset": 10800,
    "localDT": "1969-09-02 12:30:00"
  },
  {
    "gmtDT": "1969-09-02 09:30:00",
    "timeZone": "Asia/Shanghai",
    "abbreviation": "CST",
    "gmtOffset": 28800,
    "localDT": "1969-09-02 17:30:00"
  }
]
```

Загрузка БД:

```shell
mvn package exec:java
```

**Ресурсы**

[TimezoneDB](https://timezonedb.com/files/timezonedb.csv.zip) - история gmt_offset для разных timezone

[Список городов](https://raw.githubusercontent.com/kevinroberts/city-timezones/master/data/cityMap.json) - cоответствие
городов и timezone (города на латинице)

