package ru.neoflex.emf.timezonedb;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SpringBootApplication
public class LoaderApplication {
    @Autowired
    ApplicationContext context;
    public static void main(String[] args) {
        try {
            Path tempDir = Files.createTempDirectory("timezonedb.");
            try {
                URL url = new URL("https://timezonedb.com/files/timezonedb.csv.zip");
                try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream())) {
                    Path zip = tempDir.resolve("timezonedb.csv.zip");
                    try (FileChannel fileChannel = FileChannel.open(zip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                    }
                    try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zip))) {
                        for (ZipEntry ze; (ze = zipIn.getNextEntry()) != null; ) {
                            if (!ze.isDirectory()) {
                                Path csv = tempDir.resolve(ze.getName());
                                Files.copy(zipIn, csv);
                            }
                        }
                    }
                    try {
                        Path sourceDir = Paths.get(Thread.currentThread().getContextClassLoader().getResource("cityBase/cityBase.csv").toURI());
                        Path targetPath = Paths.get(tempDir + "\\cityBase.csv");
                        Files.copy(sourceDir, targetPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        Path sourceDir = Paths.get(Thread.currentThread().getContextClassLoader().getResource("cityBase/countryBase.csv").toURI());
                        Path targetPath = Paths.get(tempDir + "\\countryBase.csv");
                        Files.copy(sourceDir, targetPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                String loaddb = new String(
                        Files.readAllBytes(Paths.get(Thread.currentThread().getContextClassLoader().getResource("loaddb.sql").toURI())
                        ), StandardCharsets.UTF_8);
                String[] sqls = String.format(loaddb, tempDir.toString().replace("\\", "/")).split(";");
                ComboPooledDataSource pool = TimezoneDBSvc.getDataSource();
                try {
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(pool);
                    for (String sql: sqls) {
                        jdbcTemplate.update(sql);
                    }
                }
                finally {
                    pool.close();
                }
            }
            finally {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
