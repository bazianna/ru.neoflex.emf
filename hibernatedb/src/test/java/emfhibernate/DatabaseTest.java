package emfhibernate;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.Assert;
import org.junit.Test;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.hibernatedb.HBDBServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

public class DatabaseTest {
    @Test
    public void dbTest() throws IOException {
        try (HBDBServer server = new HBDBServer("mydb", new Properties(), new ArrayList<>());) {
            try (Session session = server.createSession()) {
                Transaction tx = session.beginTransaction();
                session.createQuery("from DBResource", DBResource.class).getResultStream().forEach(dbResource -> {
                    session.delete(dbResource);
                });
                DBResource dbResource = new DBResource();
                dbResource.setId(EcoreUtil.generateUUID());
                dbResource.setVersion("0");
                dbResource.setImage("12345".getBytes());
                dbResource.setNames(new HashSet<>(Arrays.asList("Person:Orlov", "Person:Ivanov", "Org:Neoflex")));
                session.persist(dbResource);
                tx.commit();
            }
            try (Session session = server.createSession()) {
                Query query = session.createQuery("select r from DBResource r join r.names name where name like 'Person:%'");
                Assert.assertEquals(2, query.getResultStream().count());
            }
        }
    }
}
