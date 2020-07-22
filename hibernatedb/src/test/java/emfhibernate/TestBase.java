package emfhibernate;

import org.eclipse.emf.ecore.EPackage;
import ru.neoflex.emf.hibernatedb.HBDBServer;
import ru.neoflex.emf.hibernatedb.test.TestPackage;

import java.io.File;
import java.util.ArrayList;

public class TestBase {
    public static final String HBDB = "hbtest";
    HBDBServer hbdbServer;

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static HBDBServer getDatabase() throws Exception {
        HBDBServer server = new HBDBServer(new ArrayList<EPackage>(){{add(TestPackage.eINSTANCE);}}, HBDB);
        server.setTenantId("public");
        return server;
    }

    public static File getDatabaseFile() {
        return new File(System.getProperty("user.home") + "/.h2home");
    }

    public static HBDBServer refreshDatabase() throws Exception {
        deleteDirectory(getDatabaseFile());
        return getDatabase();
    }
}
