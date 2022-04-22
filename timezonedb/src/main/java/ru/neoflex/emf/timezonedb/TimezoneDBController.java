package ru.neoflex.emf.timezonedb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

@RestController()
@RequestMapping("/timezonedb")
public class TimezoneDBController {
    @Autowired
    TimezoneDBSvc timezoneDBSvc;
    @GetMapping("/toGMT")
    public TimeShift toGMT(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date localDT,
            @RequestParam String timeZone) throws URISyntaxException, IOException {
        return timezoneDBSvc.toGMT(localDT, timeZone);
    }

    @GetMapping("/toLocal")
    public TimeShift toLocal(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date gmtDT,
            @RequestParam String timeZone) throws IOException, URISyntaxException {
        return timezoneDBSvc.toLocal(gmtDT, timeZone);
    }

    @GetMapping("/timeShift")
    public List<TimeShift> timeShift(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date fromDT,
            @RequestParam String fromZone,
            @RequestParam String toZone) throws IOException, URISyntaxException, ParseException {
        return timezoneDBSvc.timeShift(fromDT, fromZone, toZone);
    }

    @GetMapping("/toSolar")
    public SolarTime toSolar(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date gmtDT,
            @RequestParam String city) throws IOException, URISyntaxException {
        return timezoneDBSvc.toSolar(gmtDT, city);
    }
}
