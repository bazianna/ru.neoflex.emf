package emfhibernate;

import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.hibernatedb.test.TestPackage;

import java.io.File;
import java.util.Properties;

public class TestBase {
    public static final String HBDB = "hbtest";
    DBServer dbServer;

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static DBServer getDatabase() throws Exception {
        Properties properties = new Properties();
        //properties.setProperty("emfdb.hb.show_sql", "true");
        DBServer server = new DBServer(HBDB, properties);
        server.registerEPackage(TestPackage.eINSTANCE);
        return server;
    }

    public static File getDatabaseFile() {
        return new File(System.getProperty("user.home") + "/.h2home");
    }

    public static DBServer refreshDatabase() throws Exception {
        deleteDirectory(getDatabaseFile());
        return getDatabase();
    }
}
