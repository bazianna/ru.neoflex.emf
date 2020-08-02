package emfhibernate;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.Assert;
import org.junit.Test;
import ru.neoflex.emf.base.DBObject;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.hibernatedb.HBDBServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class DatabaseTest {
    @Test
    public void dbTest() {
        try (HBDBServer server = new HBDBServer("mydb", new Properties(), new ArrayList<>())) {
            try (Session session = server.createSession()) {
                Transaction tx = session.beginTransaction();
                session.createQuery("select r from DBResource r", DBResource.class).getResultStream().forEach(session::delete);
                DBResource dbResource = new DBResource();
                dbResource.setId(EcoreUtil.generateUUID());
                dbResource.setVersion("0");
                dbResource.setImage("12345".getBytes());
                dbResource.setDbObjects(Arrays.asList(
                        new DBObject("Person", "Orlov"),
                        new DBObject("Person", "Ivanov"),
                        new DBObject("Org", "Neoflex")));
                session.persist(dbResource);
                tx.commit();
            }
            try (Session session = server.createSession()) {
                Query query = session.createQuery("select r from DBResource r join r.dbObjects name where name.classUri = 'Person'");
                Assert.assertEquals(2, query.getResultStream().count());
            }
        }
    }
}
