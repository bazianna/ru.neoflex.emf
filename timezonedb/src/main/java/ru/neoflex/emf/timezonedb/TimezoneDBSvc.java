package ru.neoflex.emf.timezonedb;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class TimezoneDBSvc {
    public static final String JDBC_TIMEZONE_DB = "jdbc:h2:file:~/.h2home/timezone";
    public static final String JDBC_DRIVER = "org.h2.Driver";
    public static final String JDBC_USER = "sa";
    public static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    ComboPooledDataSource pool;

    @PostConstruct
    void init() throws PropertyVetoException {
        pool = getDataSource();
    }

    public static ComboPooledDataSource getDataSource() throws PropertyVetoException {
        ComboPooledDataSource pool = new ComboPooledDataSource();
        pool.setJdbcUrl(JDBC_TIMEZONE_DB);
        pool.setDriverClass(JDBC_DRIVER);
        pool.setUser(JDBC_USER);
        return pool;
    }

    @PreDestroy
    void fini() {
        pool.close();
    }

    public TimeShift toGMT(Date localDT, String timeZone) throws URISyntaxException, IOException {
        return getTimeShiftByTemplate("togmt.sql", localDT, timeZone);
    }

    private TimeShift getTimeShiftByTemplate(String templateName, Date dt, String timeZone) throws IOException, URISyntaxException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(pool);
        String template = new String(
                Files.readAllBytes(Paths.get(Thread.currentThread().getContextClassLoader().getResource(templateName).toURI())
                ), StandardCharsets.UTF_8);
        String sql = String.format(template, DATE_TIME_FORMAT.format(dt), timeZone);
        List<TimeShift> timeShifts = jdbcTemplate.query(sql, (resultSet, i) -> {
            TimeShift timeShift = new TimeShift();
            timeShift.setTimeZone(resultSet.getString(1));
            timeShift.setGmtOffset(resultSet.getInt(2));
            timeShift.setGmtDT(DATE_TIME_FORMAT.format(resultSet.getTimestamp(3)));
            timeShift.setLocalDT(DATE_TIME_FORMAT.format(resultSet.getTimestamp(4)));
            timeShift.setAbbreviation(resultSet.getString(5));
            return timeShift;
        });
        if (timeShifts.size() == 0) {
            throw new IllegalArgumentException(timeZone);
        }
        return timeShifts.get(0);
    }

    private SolarTime getTimeShiftByTemplateSolar(String templateName, Date dt, String city) throws IOException, URISyntaxException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(pool);
        String template = new String(
                Files.readAllBytes(Paths.get(Thread.currentThread().getContextClassLoader().getResource(templateName).toURI())
                ), StandardCharsets.UTF_8);
        String sql = String.format(template, DATE_TIME_FORMAT.format(dt), city);
        List<SolarTime> solarTimes = jdbcTemplate.query(sql, (resultSet, i) -> {
            SolarTime solarTime = new SolarTime();
            solarTime.setSolarDT(resultSet.getString(1));
            return solarTime;
        });
        if (solarTimes.size() == 0) {
            throw new IllegalArgumentException(city);
        }
        return solarTimes.get(0);
    }

     public TimeShift toLocal(Date gmtDT, String timeZone) throws IOException, URISyntaxException {
        return getTimeShiftByTemplate("tolocal.sql", gmtDT, timeZone);
    }

    public SolarTime getSolar(Date gmtDT, String city) throws IOException, URISyntaxException {
        return getTimeShiftByTemplateSolar("getSolar.sql", gmtDT, city);
    }

    public List<TimeShift> timeShift(Date fromDT, String fromZone, String toZone) throws IOException, URISyntaxException, ParseException {
        List<TimeShift> timeShifts = new ArrayList<>();
        timeShifts.add(toGMT(fromDT, fromZone));
        timeShifts.add(toLocal(DATE_TIME_FORMAT.parse(timeShifts.get(0).getGmtDT()), toZone));
        return timeShifts;
    }
}
