package emfhibernate;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.base.DBTransaction;
import ru.neoflex.emf.hibernatedb.test.*;

import java.io.IOException;
import java.sql.Statement;
import java.util.List;
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
        dbServer.inTransaction(false, DBTransaction::truncate);
        Group group = TestFactory.eINSTANCE.createGroup();
        Long[] ids = dbServer.inTransaction(false, tx -> {
            group.setName("masters");
            ResourceSet resourceSet = tx.getResourceSet();
            Resource groupResource = resourceSet.createResource(dbServer.createURI(null));
            groupResource.getContents().add(group);
            groupResource.save(null);
            Long groupId = dbServer.getId(groupResource.getURI());
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            Resource userResource = resourceSet.createResource(dbServer.createURI(null));
            userResource.getContents().add(user);
            userResource.save(null);
            Long userId = dbServer.getId(user);
            Assert.assertNotNull(userId);
            return new Long[]{userId, groupId};
        });
        dbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
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
            ResourceSet resourceSet = tx.getResourceSet();
            Resource userResource = resourceSet.createResource(dbServer.createURI(null));
            userResource.getContents().add(user);
            userResource.save(null);
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            tx.getSession().doWork(connection -> {
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
    public void dbTest() throws Exception {
        dbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();

            DBTable group = TestFactory.eINSTANCE.createDBTable();
            group.setName("GROUP");
            Column group_id = TestFactory.eINSTANCE.createColumn();
            group_id.setName("ID");
            group_id.setDbType("INTEGER");
            group.getColumns().add(group_id);
            Column group_name = TestFactory.eINSTANCE.createColumn();
            group_name.setName("NAME");
            group_name.setDbType("STRING");
            group.getColumns().add(group_name);
            PKey group_pk = TestFactory.eINSTANCE.createPKey();
            group_pk.setName("group_pk");
            group_pk.getColumns().add(group_id);
            group.setPKey(group_pk);
            Resource group_res = rs.createResource(dbServer.createURI());
            group_res.getContents().add(group);
            group_res.save(null);

            DBTable user = TestFactory.eINSTANCE.createDBTable();
            user.setName("USER");
            Column user_id = TestFactory.eINSTANCE.createColumn();
            user_id.setName("ID");
            user_id.setDbType("INTEGER");
            user.getColumns().add(user_id);
            Column user_name = TestFactory.eINSTANCE.createColumn();
            user_name.setName("NAME");
            user_name.setDbType("STRING");
            user.getColumns().add(user_name);
            Column user_group_id = TestFactory.eINSTANCE.createColumn();
            user_group_id.setName("GROUP_ID");
            user_group_id.setDbType("INTEGER");
            user.getColumns().add(user_group_id);
            PKey user_pk = TestFactory.eINSTANCE.createPKey();
            user_pk.setName("user_pk");
            user_pk.getColumns().add(user_id);
            user.setPKey(user_pk);
            FKey user_group_fk = TestFactory.eINSTANCE.createFKey();
            user_group_fk.setName("user_group_fk");
            user_group_fk.getColumns().add(user_group_id);
            user_group_fk.setEntity(group);
            user.getFKeys().add(user_group_fk);
            Resource user_res = rs.createResource(dbServer.createURI());
            user_res.getContents().add(user);
            user_res.save(null);

            DBView user_group = TestFactory.eINSTANCE.createDBView();
            user_group.setName("USER_GROUP");
            user_group.getColumns().add(user_id);
            user_group.getColumns().add(user_name);
            user_group.getColumns().add(group_id);
            user_group.getColumns().add(group_name);
            Resource user_group_res = rs.createResource(dbServer.createURI());
            user_group_res.getContents().add(user_group);
            user_group_res.save(null);
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r->((DBTable)r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            Assert.assertEquals(1, users.size());
            DBTable user = (DBTable) users.get(0).getContents().get(0);
            Assert.assertEquals("GROUP_ID", user.getFKeys().get(0).getColumns().get(0).getName());
            Assert.assertEquals("ID", user.getPKey().getColumns().get(0).getName());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            Assert.assertEquals("ID", user_group.getColumns().get(0).getName());
            Assert.assertEquals("NAME", user_group.getColumns().get(1).getName());
            Assert.assertEquals(4, user_group.getColumns().size());
            Resource table_User = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r->((DBTable)r.getContents().get(0)).getName().equals("USER")).findFirst().get();
            try {
                table_User.delete(null);
                Assert.fail("Can't delete referenced Resource");
            }
            catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().startsWith("Can not delete"));
            }
            return null;
        });
        DBView extView = dbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r->((DBTable)r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            DBTable user = (DBTable) users.get(0).getContents().get(0);
            Column group_id = user.getColumns().stream().filter(column -> column.getName().equals("GROUP_ID")).findFirst().get();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            user_group.getColumns().add(0, group_id);
            user_group.eResource().save(null);
            return user_group;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            Assert.assertEquals("GROUP_ID", user_group.getColumns().get(0).getName());
            Assert.assertEquals("ID", user_group.getColumns().get(1).getName());
            Assert.assertEquals(5, user_group.getColumns().size());
            return null;
        });
        dbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r->((DBTable)r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            Resource userRes = users.get(0);
            List<Resource> refs = tx.findReferencedTo(userRes).collect(Collectors.toList());
            Assert.assertEquals(1, refs.size());
            return  null;
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
