package emfmem;

import ru.neoflex.emf.memdb.MemDBServer;
import ru.neoflex.emf.memdb.test.TestPackage;

import java.io.File;
import java.util.Properties;

public class TestBase {
    public static final String MEMDB = "test-emf-mem";
    MemDBServer memDBServer;

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static MemDBServer getDatabase() throws Exception {
        Properties config = new Properties();
        config.put(MemDBServer.CONFIG_PREVALENCE_BASE, getDatabaseFile().getAbsolutePath());
        MemDBServer dbServer = new MemDBServer(MEMDB, config);
        dbServer.registerEPackage(TestPackage.eINSTANCE);
        return dbServer;
    }

    public static File getDatabaseFile() {
        return new File(System.getProperty("user.home") + "/.memdb", MEMDB);
    }

    public static MemDBServer refreshDatabase() throws Exception {
        deleteDirectory(getDatabaseFile());
        return getDatabase();
    }
}
