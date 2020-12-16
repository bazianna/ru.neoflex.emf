package emfhibernate;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.base.HbTransaction;
import ru.neoflex.emf.hibernatedb.test.*;

import java.io.IOException;
import java.sql.Statement;
//import java.io.ByteArrayOutputStream;
//import org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup;
//import org.eclipse.xtext.resource.XtextResourceSet;

public class DatabaseTests extends TestBase {
    @Before
    public void startUp() throws Exception {
        hbServer = refreshDatabase();
    }

    @After
    public void shutDown() throws IOException {
        hbServer.close();
    }

    @Test
    public void testMetaView() throws Exception {
        DBTable aObject = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.createResourceSet();
            DBTable testTable = TestFactory.eINSTANCE.createDBTable();
            testTable.setName("TEST_TABLE");
            Resource testRes = rs.createResource(hbServer.createURI());
            testRes.getContents().add(testTable);
            testRes.save(null);
            MetaView metaView = TestFactory.eINSTANCE.createMetaView();
            metaView.setName("My Meta View");
            metaView.setAPackage(TestPackage.eINSTANCE);
            metaView.setAClass(EcorePackage.eINSTANCE.getEAnnotation());
            metaView.setAObject(testTable);
            Resource metaRes = rs.createResource(hbServer.createURI());
            metaRes.getContents().add(metaView);
            metaRes.save(null);
            return testTable;
        });
        MetaView metaView = hbServer.inTransaction(true, tx -> {
            Resource metaViewRes = hbServer.findBy(tx.getResourceSet(), TestPackage.eINSTANCE.getMetaView(), "My Meta View");
            EcoreUtil.resolveAll(metaViewRes);
            return (MetaView) metaViewRes.getContents().get(0);
        });
        Assert.assertEquals(EcorePackage.eINSTANCE.getEAnnotation(), metaView.getAClass());
        Assert.assertEquals(TestPackage.eINSTANCE, metaView.getAPackage());
        Assert.assertEquals(aObject.getName(), ((DBTable) metaView.getAObject()).getName());
        hbServer.inTransaction(false, tx -> {
            metaView.setAClass(EcorePackage.eINSTANCE.getEOperation());
            ResourceSet rs = tx.createResourceSet();
            Resource resource = rs.createResource(metaView.eResource().getURI());
            resource.setTimeStamp(metaView.eResource().getTimeStamp());
            resource.getContents().add(metaView);
            resource.save(null);
            return null;
        });
        Resource metaViewRes2 = hbServer.inTransaction(true, tx ->
                hbServer.findBy(tx.getResourceSet(), TestPackage.eINSTANCE.getMetaView(), "My Meta View")
        );
        MetaView metaView2 = (MetaView) metaViewRes2.getContents().get(0);
        Assert.assertEquals(EcorePackage.eINSTANCE.getEOperation(), metaView2.getAClass());
    }

    @Test
    public void createEMFObject() throws Exception {
        hbServer.inTransaction(false, HbTransaction::truncate);
        Group group = TestFactory.eINSTANCE.createGroup();
        Long[] ids = hbServer.inTransaction(false, tx -> {
            group.setName("masters");
            ResourceSet resourceSet = tx.getResourceSet();
            Resource groupResource = resourceSet.createResource(hbServer.createURI());
            groupResource.getContents().add(group);
            groupResource.save(null);
            Long groupId = hbServer.getId(group);
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            Resource userResource = resourceSet.createResource(hbServer.createURI());
            userResource.getContents().add(user);
            userResource.save(null);
            Long userId = hbServer.getId(user);
            Assert.assertNotNull(userId);
            return new Long[]{userId, groupId};
        });
        hbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
            Resource userResource = resourceSet.createResource(hbServer.createURI(ids[0]));
            userResource.load(null);
            User user = (User) userResource.getContents().get(0);
            user.setName("Simanihin");
            userResource.save(null);
            return null;
        });
        hbServer.inTransaction(false, tx -> {
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            ResourceSet resourceSet = tx.getResourceSet();
            Resource userResource = resourceSet.createResource(hbServer.createURI());
            userResource.getContents().add(user);
            userResource.save(null);
            Assert.assertEquals(3, hbServer.findAll(resourceSet).getContents().size());
            Assert.assertEquals(2, hbServer.findBy(resourceSet, TestPackage.Literals.USER).getContents().size());
            Assert.assertEquals(2, hbServer.findReferencedTo(resourceSet, group.eResource()).getContents().size());
            Assert.assertEquals(1, hbServer.findBy(resourceSet, TestPackage.Literals.USER, "Simanihin").getContents().size());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
            Assert.assertEquals(3, hbServer.findAll(resourceSet).getContents().size());
            Assert.assertEquals(2, hbServer.findBy(resourceSet, TestPackage.Literals.USER).getContents().size());
            Assert.assertEquals(2, hbServer.findReferencedTo(resourceSet, group.eResource()).getContents().size());
            Assert.assertEquals(1, hbServer.findBy(resourceSet, TestPackage.Literals.USER, "Simanihin").getContents().size());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            tx.getSession().doWork(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("create schema TEST");
                }
            });
            return null;
        });
        hbServer.setSchema("TEST");
    }

    @Test
    public void dbTest() throws Exception {
        hbServer.inTransaction(false, tx -> {
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
            Resource group_res = rs.createResource(hbServer.createURI());
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
            Resource user_res = rs.createResource(hbServer.createURI());
            user_res.getContents().add(user);
            user_res.save(null);

            DBView user_group = TestFactory.eINSTANCE.createDBView();
            user_group.setName("USER_GROUP");
            user_group.getColumns().add(user_id);
            user_group.getColumns().add(user_name);
            user_group.getColumns().add(group_id);
            user_group.getColumns().add(group_name);
            Resource user_group_res = rs.createResource(hbServer.createURI());
            user_group_res.getContents().add(user_group);
            user_group_res.save(null);
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource users = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBTable(), TestPackage.eINSTANCE.getDBEntity_Name(), "USER");
            Assert.assertEquals(1, users.getContents().size());
            EcoreUtil.resolveAll(users);
            DBTable user = (DBTable) users.getContents().get(0);
            Assert.assertEquals("GROUP_ID", user.getFKeys().get(0).getColumns().get(0).getName());
            Assert.assertEquals("ID", user.getPKey().getColumns().get(0).getName());
            return null;
        });
        try {
            hbServer.inTransaction(false, tx -> {
                ResourceSet rs = tx.getResourceSet();
                Resource views = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBView());
                DBView user_group = (DBView) views.getContents().get(0);
                Assert.assertEquals("ID", user_group.getColumns().get(0).getName());
                Assert.assertEquals("NAME", user_group.getColumns().get(1).getName());
                Assert.assertEquals(4, user_group.getColumns().size());
                EObject table_User = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBTable(), TestPackage.eINSTANCE.getDBEntity_Name(), "USER").getContents().get(0);
                Resource resource = rs.createResource(hbServer.createURI(table_User));
                resource.delete(null);
                return null;
            });
            Assert.fail("Can't delete referenced Resource");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("ConstraintViolation"));
        }
        try {
            hbServer.inTransaction(false, tx -> {
                ResourceSet rs = tx.getResourceSet();
                Resource resource = rs.createResource(hbServer.createURI());
                DBTable group = TestFactory.eINSTANCE.createDBTable();
                group.setName("GROUP");
                resource.getContents().add(group);
                resource.save(null);
                return null;
            });
            Assert.fail("Can't insert duplicated EObject Resource");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Duplicate"));
        }
        DBView extView = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource users = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBTable(), TestPackage.eINSTANCE.getDBEntity_Name(), "USER");
            DBTable user = (DBTable) users.getContents().get(0);
            Column group_id = user.getColumns().stream().filter(column -> column.getName().equals("GROUP_ID")).findFirst().get();
            Resource views = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBView());
            DBView user_group = (DBView) views.getContents().get(0);
            user_group.getColumns().add(0, group_id);
            user_group.eResource().save(null);
            return user_group;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource views = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBView());
            DBView user_group = (DBView) views.getContents().get(0);
            Assert.assertEquals("GROUP_ID", user_group.getColumns().get(0).getName());
            Assert.assertEquals("ID", user_group.getColumns().get(1).getName());
            Assert.assertEquals(5, user_group.getColumns().size());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource users = hbServer.findBy(rs, TestPackage.eINSTANCE.getDBTable(), TestPackage.eINSTANCE.getDBEntity_Name(), "USER");
            Resource refs = hbServer.findReferencedTo(rs, users);
            Assert.assertEquals(1, refs.getContents().size());
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
