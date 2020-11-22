package emfhibernate;

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
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
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

    ViewBase createView(String prefix, int deep, int wide) {
        if (deep > 1) {
            ViewContainer viewContainer = TestFactory.eINSTANCE.createViewContainer();
            viewContainer.setElementName(prefix);
            for (int w = 0; w < wide; ++w) {
                ViewBase child = createView(prefix + "_" + w, deep - 1, wide);
                viewContainer.getElements().add(child);
                child.setFirstSibling(child.getParent().getElements().get(0));
            }
            return viewContainer;
        }
        else {
            ViewElement viewElement = TestFactory.eINSTANCE.createViewElement();
            viewElement.setElementName(prefix);
            viewElement.setCreated(new Date());
            for (int w = 0; w < wide; ++w) {
                viewElement.getWeights().add(new BigDecimal(w));
            }
            return viewElement;
        }
    }

    @Test
    public void testDeep() throws Exception {
        long count0 = hbServer.getEObjectToIdMap().size();
        long start = System.currentTimeMillis();
        ViewBase view1 = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource resource = rs.createResource(tx.getDbServer().createURI());
            ViewBase viewBase = createView("", 3, 100);
            resource.getContents().add(viewBase);
            resource.save(null);
            return viewBase;
        });
        Long id = hbServer.getId(view1);
        Assert.assertNotNull(id);
        long afterInsert = System.currentTimeMillis();
        long count = hbServer.getEObjectToIdMap().size() - count0;
        ViewBase view2 = hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource resource = rs.createResource(tx.getDbServer().createURI(id));
            resource.load(null);
            Assert.assertEquals(1, resource.getContents().size());
            return (ViewBase) resource.getContents().get(0);
        });
        Assert.assertEquals("", view2.getElementName());
        long afterLoad = System.currentTimeMillis();
        System.out.println("Created " + count + " objects");
        System.out.println("Inserted in " + (afterInsert-start)/1000 + "s. " + (afterInsert-start)*1000/count + " ms/1000*object.");
        System.out.println("Loaded in " + (afterLoad-afterInsert)/1000 + "s. " + (afterLoad-afterInsert)*1000/count + " ms/1000*object.");
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
            Resource metaViewRes = tx.findByClassAndQName(tx.getResourceSet(), TestPackage.eINSTANCE.getMetaView(), "My Meta View")
                    .findFirst().get();
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
            resource.getContents().add(metaView);
            resource.save(null);
            return null;
        });
        Resource metaViewRes2 = hbServer.inTransaction(true, tx ->
                tx.findByClassAndQName(tx.getResourceSet(), TestPackage.eINSTANCE.getMetaView(), "My Meta View")
                        .findFirst().get()
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
            Resource groupResource = resourceSet.createResource(hbServer.createURI(null));
            groupResource.getContents().add(group);
            groupResource.save(null);
            Long groupId = hbServer.getId(groupResource.getURI());
            User user = TestFactory.eINSTANCE.createUser();
            user.setName("Orlov");
            user.setGroup(group);
            Resource userResource = resourceSet.createResource(hbServer.createURI(null));
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
            Resource userResource = resourceSet.createResource(hbServer.createURI(null));
            userResource.getContents().add(user);
            userResource.save(null);
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
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
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r -> ((DBTable) r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            Assert.assertEquals(1, users.size());
            DBTable user = (DBTable) users.get(0).getContents().get(0);
            Assert.assertEquals("GROUP_ID", user.getFKeys().get(0).getColumns().get(0).getName());
            Assert.assertEquals("ID", user.getPKey().getColumns().get(0).getName());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            Assert.assertEquals("ID", user_group.getColumns().get(0).getName());
            Assert.assertEquals("NAME", user_group.getColumns().get(1).getName());
            Assert.assertEquals(4, user_group.getColumns().size());
            Resource table_User = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r -> ((DBTable) r.getContents().get(0)).getName().equals("USER")).findFirst().get();
            try {
                table_User.delete(null);
                Assert.fail("Can't delete referenced Resource");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().startsWith("Can not delete"));
            }
            return null;
        });
        DBView extView = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r -> ((DBTable) r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            DBTable user = (DBTable) users.get(0).getContents().get(0);
            Column group_id = user.getColumns().stream().filter(column -> column.getName().equals("GROUP_ID")).findFirst().get();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            user_group.getColumns().add(0, group_id);
            user_group.eResource().save(null);
            return user_group;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> views = tx.findByClass(rs, TestPackage.eINSTANCE.getDBView()).collect(Collectors.toList());
            DBView user_group = (DBView) views.get(0).getContents().get(0);
            Assert.assertEquals("GROUP_ID", user_group.getColumns().get(0).getName());
            Assert.assertEquals("ID", user_group.getColumns().get(1).getName());
            Assert.assertEquals(5, user_group.getColumns().size());
            return null;
        });
        hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            List<Resource> users = tx.findByClass(rs, TestPackage.eINSTANCE.getDBTable()).filter(r -> ((DBTable) r.getContents().get(0)).getName().equals("USER")).collect(Collectors.toList());
            Resource userRes = users.get(0);
            List<Resource> refs = tx.findReferencedTo(userRes).collect(Collectors.toList());
            Assert.assertEquals(1, refs.size());
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
