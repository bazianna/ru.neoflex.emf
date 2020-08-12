package emfgit;

import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.gitdb.GitDBFactory;
import ru.neoflex.emf.gitdb.GitDBServer;
import ru.neoflex.emf.gitdb.test.TestPackage;

import java.io.File;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TestBase {
    public static final String DB_NAME = "dbtest";
    GitDBServer dbServer;

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static GitDBServer getDatabase() throws Exception {
        ServiceLoader<DBFactory> loader = ServiceLoader.load(DBFactory.class);
        DBFactory dbFactory = StreamSupport.stream(loader.spliterator(), false)
//                .filter(f->f.getClass().getName().equals(GitDBFactory.class.getName()))
                .findFirst().get();
        GitDBServer server = (GitDBServer) dbFactory.create(DB_NAME, new Properties());
        server.registerEPackage(TestPackage.eINSTANCE);
        server.setTenantId("master");
        return server;
    }

    public static File getDatabaseFile() {
        return new File(System.getProperty("user.home") + "/.githome/" + DB_NAME);
    }

    public static GitDBServer refreshDatabase() throws Exception {
        deleteDirectory(getDatabaseFile());
        return getDatabase();
    }
}
