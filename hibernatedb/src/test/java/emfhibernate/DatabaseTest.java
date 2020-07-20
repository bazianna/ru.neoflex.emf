package emfhibernate;

import org.junit.Test;
import ru.neoflex.emf.hibernatedb.HBDBServer;

import java.io.IOException;
import java.util.ArrayList;

public class DatabaseTest {
    @Test
    public void dbTest() throws IOException {
        try (HBDBServer server = new HBDBServer(new ArrayList<>(), "mydb");) {

        }
    }
}
