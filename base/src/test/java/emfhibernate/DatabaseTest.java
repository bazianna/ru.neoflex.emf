package emfhibernate;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.base.DBObject;
import ru.neoflex.emf.base.DBReference;
import ru.neoflex.emf.base.DBServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class DatabaseTest extends TestBase {
    @Before
    public void startUp() throws Exception {
        dbServer = refreshDatabase();
    }

    @After
    public void shutDown() throws IOException {
        dbServer.close();
    }

    @Test
    public void dbTest() {
        Long id;
        try (DBServer server = new DBServer("mydb", new Properties())) {
            try (Session session = server.createSession()) {
                Transaction tx = session.beginTransaction();
                session.createQuery("select r from DBObject r", DBObject.class).getResultStream().forEach(session::delete);
                DBObject dbObject = new DBObject();
                dbObject.setVersion(0);
                dbObject.setImage("12345".getBytes());
                dbObject.setReferences(Arrays.asList(
                        new DBReference() {{
                            setContainment(true);
                            setFeature("self");
                            setIndex(-1);
                            setRefObject(dbObject);}}
                ));
                session.persist(dbObject);
                tx.commit();
                id = dbObject.getId();
            }
            try (Session session = server.createSession()) {
                Query query = session.createQuery("select r from DBObject o join o.references r where r.refObject.id = " + id);
                Assert.assertEquals(1, query.getResultStream().count());
            }
        }
    }
}
