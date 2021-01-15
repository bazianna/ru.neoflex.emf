package emfhibernate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.neoflex.emf.base.HbServer;
import ru.neoflex.emf.hibernatedb.test.TestPackage;

import java.io.File;
import java.util.Properties;

public class TestBase {
    private static final Logger logger = LoggerFactory.getLogger(TestBase.class);
    public static final String HBDB = "hbtest";
    HbServer hbServer;

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        boolean result = directoryToBeDeleted.delete();
        logger.info(String.format("Deleting %s: %s", directoryToBeDeleted.getAbsolutePath(), String.valueOf(result)));
        return result;
    }

    public static HbServer getDatabase() throws Exception {
        Properties properties = new Properties();
//        properties.setProperty("hb.show_sql", "true");
//        properties.setProperty("hb.dialect", "org.hibernate.dialect.PostgreSQLDialect");
//        properties.setProperty("hb.driver", "org.postgresql.Driver");
//        properties.setProperty("hb.url", "jdbc:postgresql://localhost:5432/hbtest");
//        properties.setProperty("hb.user", "postgres");
//        properties.setProperty("hb.pass", "ne0f1ex");
        HbServer server = new HbServer(HBDB, properties);
        server.registerEPackage(TestPackage.eINSTANCE);
        return server;
    }

    public static File getDatabaseFile() {
        return new File(System.getProperty("user.home") + "/.h2home");
    }

    public static HbServer refreshDatabase() throws Exception {
        deleteDirectory(getDatabaseFile());
        return getDatabase();
    }
}
