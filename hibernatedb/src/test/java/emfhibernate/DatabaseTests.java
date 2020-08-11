package emfhibernate;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.hibernatedb.HBDBTransaction;
import ru.neoflex.emf.hibernatedb.test.Group;
import ru.neoflex.emf.hibernatedb.test.TestFactory;
import ru.neoflex.emf.hibernatedb.test.TestPackage;
import ru.neoflex.emf.hibernatedb.test.User;

import java.io.IOException;
import java.sql.Statement;
import java.util.stream.Collectors;
//import java.io.ByteArrayOutputStream;
//import org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup;
//import org.eclipse.xtext.resource.XtextResourceSet;

public class DatabaseTests extends TestBase {
    @Before
    public void startUp() throws Exception {
        dbServer = refreshDatabase();
    }

    @After
    public void shutDown() throws IOException {
        dbServer.close();
    }

    @Test
    public void createEMFObject() throws Exception {
        dbServer.inTransaction(false, tx -> {
            for (Resource resource: tx.findAll(tx.createResourceSet()).collect(Collectors.toList())) {
                resource.delete(null);
            }
            return null;
        });
        Group group = TestFactory.eINSTANCE.createGroup();
        String[] ids = dbServer.inTransaction(false, tx -> {
            group.setName("masters");
            ResourceSet resourceSet = tx.createResourceSet();
            Resource groupResource = resourceSet.createResource(dbServer.createURI(""));
            groupResource.getContents().add(group);
            groupResource.save(null);
            String groupId = dbServer.getId(groupResource.getURI());
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            Resource userResource = resourceSet.createResource(dbServer.createURI(""));
            userResource.getContents().add(user);
            userResource.save(null);
            String userId = dbServer.getId(userResource.getURI());
            Assert.assertNotNull(userId);
            return new String[]{userId, groupId};
        });
        dbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Resource userResource = resourceSet.createResource(dbServer.createURI(ids[0]));
            userResource.load(null);
            User user = (User) userResource.getContents().get(0);
            user.setName("Simanihin");
            userResource.save(null);
            return null;
        });
        dbServer.inTransaction(false, tx -> {
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            ResourceSet resourceSet = tx.createResourceSet();
            Resource userResource = resourceSet.createResource(dbServer.createURI(""));
            userResource.getContents().add(user);
            userResource.save(null);
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ((HBDBTransaction)tx).getSession().doWork(connection -> {
                try(Statement statement = connection.createStatement()) {
                    statement.executeUpdate("create schema TEST");
                }
            });
        return null;
        });
        dbServer.setSchema("TEST");
//        memBDServer.inTransaction(true, (MemBDServer.TxFunction<Void>) tx -> {
//            return null;
//        });
//        memBDServer.inTransaction(true, (MemBDServer.TxFunction<Void>) tx -> {
//            return null;
//        });
    }

    @Test
    public void predefinedIDTest() throws Exception {
        Group group = TestFactory.eINSTANCE.createGroup();
        String[] ids = dbServer.inTransaction(false, tx -> {
            group.setName("masters");
            ResourceSet resourceSet = tx.createResourceSet();
            Resource groupResource = resourceSet.createResource(dbServer.createURI("myproject/groups/masters"));
            groupResource.getContents().add(group);
            groupResource.save(null);
            String groupId = dbServer.getId(groupResource.getURI());
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            Resource userResource = resourceSet.createResource(dbServer.createURI("myproject/users/Orlov"));
            userResource.getContents().add(user);
            userResource.save(null);
            String userId = dbServer.getId(userResource.getURI());
            Assert.assertNotNull(userId);
            return new String[]{userId, groupId};
        });
        dbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Resource userResource = resourceSet.createResource(dbServer.createURI(ids[0]));
            userResource.load(null);
            User user = (User) userResource.getContents().get(0);
            Assert.assertEquals("Orlov", user.getName());
            Assert.assertEquals("masters", user.getGroup().getName());
            return null;
        });
    }
//    @Test
//    public void loadXcore() throws IOException {
//        XcoreStandaloneSetup.doSetup();
//        ResourceSet rs = new XtextResourceSet();
//        URI uri = URI.createURI("classpath:/metamodel/application.xcore");
//        Resource resource = rs.getResource(uri, true);
//        Assert.assertNotNull(resource);
//        EcoreResourceFactoryImpl ef = new EcoreResourceFactoryImpl();
//        Resource er = rs.createResource(URI.createURI("application.ecore"));
//        er.getContents().add(EcoreUtil.copy(resource.getContents().get(0)));
//        Assert.assertNotNull(er);
//        ByteArrayOutputStream os = new ByteArrayOutputStream();
//        er.save(os, null);
//        String ecore = os.toString();
//        Assert.assertNotNull(ecore);
//    }
}
