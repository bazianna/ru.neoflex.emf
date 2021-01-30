package emfhibernate;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.hibernatedb.test.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PerfTests extends TestBase {
    int nGroups = 50;
    int nUsers = 100;
    int nThreads = 10;
    int nUpdates = 10;
    int deep = 3;
    int wide = 10;
    int nTables = 2;
    int nColumns = 2;
    List<Long> groupIds = new ArrayList<>();
    List<Long> userIds = new ArrayList<>();

    @Before
    public void startUp() throws Exception {
        hbServer = refreshDatabase();
    }

    @After
    public void shutDown() throws IOException {
        hbServer.close();
    }

    Schema createSchema(String name, int nTables, int nColumns) {
        Schema schema = TestFactory.eINSTANCE.createSchema();
        schema.setName(name);
        for (int i = 0; i < nTables; ++i) {
            if (i <= nTables * 3 / 4) {
                DBTable dbTable = TestFactory.eINSTANCE.createDBTable();
                schema.getEntities().add(dbTable);
                dbTable.setName("table" + i);
                for (int j = 0; j < nColumns; ++j) {
                    Column column = TestFactory.eINSTANCE.createColumn();
                    dbTable.getColumns().add(column);
                    column.setName("column" + j);
                    column.setDbType("varchar(100)");
                }
                PKey pKey = TestFactory.eINSTANCE.createPKey();
                pKey.setName("table" + i + "_pkey");
                pKey.getColumns().add(dbTable.getColumns().get(0));
                dbTable.setPKey(pKey);
                for (int j = 0; j < (Math.min(nColumns - 2, 3)); ++j) {
                    IEKey ieKey = TestFactory.eINSTANCE.createIEKey();
                    dbTable.getIndexes().add(ieKey);
                    ieKey.setName("table" + i + "_pkey" + j);
                    ieKey.getColumns().add(dbTable.getColumns().get(j + 1));
                    ieKey.getColumns().add(dbTable.getColumns().get(j + 2));
                }
            } else {
                DBView dbView = TestFactory.eINSTANCE.createDBView();
                schema.getEntities().add(dbView);
                dbView.setName("view" + i);
                DBTable dbTable = (DBTable) schema.getEntities().get(i - nTables/2);
                for (int j = 0; j < nColumns; ++j) {
                    Column column = dbTable.getColumns().get(j);
                    dbView.getColumns().add(column);
                }
            }
        }
        return schema;
    }

    @Test
    public void testSchema() throws Exception {
        long count0 = hbServer.getEObjectToIdMap().size();
        long start = System.currentTimeMillis();
        Schema schema1 = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource resource = rs.createResource(tx.getHbServer().createURI());
            Schema schema = createSchema("emf", nTables, nColumns);
            resource.getContents().add(schema);
            resource.save(null);
            return schema;
        });
        Long id = hbServer.getId(schema1);
        Assert.assertNotNull(id);
        long afterInsert = System.currentTimeMillis();
        long count = hbServer.getEObjectToIdMap().size() - count0;
        Schema schema2 = hbServer.inTransaction(true, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource resource = rs.createResource(tx.getHbServer().createURI(id));
            resource.load(null);
//            EcoreUtil.resolveAll(rs);
            Assert.assertEquals(1, resource.getContents().size());
            return (Schema) resource.getContents().get(0);
        });
        long afterLoad = System.currentTimeMillis();
        Assert.assertEquals("emf", schema2.getName());
        Assert.assertEquals(schema1.getEntities().size(), schema2.getEntities().size());
        Schema schema3 = hbServer.inTransaction(false, tx -> {
            ResourceSet rs = tx.createResourceSet();
            Resource resource = rs.createResource(tx.getHbServer().createURI(schema2));
            resource.getContents().add(schema2);
            schema2.getEntities().get(0).setName("newName");
            resource.save(null);
            Assert.assertEquals(1, resource.getContents().size());
            return (Schema) resource.getContents().get(0);
        });
        long afterUpdate = System.currentTimeMillis();
        Assert.assertEquals("newName", schema3.getEntities().get(0).getName());
        System.out.println("Created " + count + " objects");
        System.out.println("Inserted in " + (afterInsert - start) / 1000 + "s. " + count * 1000 / (afterInsert - start) + " object/s.");
        System.out.println("Loaded in " + (afterLoad - afterInsert) / 1000 + "s. " + count * 1000 / (afterLoad - afterInsert) + " object/s.");
        System.out.println("Updated in " + (afterUpdate - afterLoad) / 1000 + "s. " + count * 1000 / (afterUpdate - afterLoad) + " object/s.");
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
        } else {
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
            Resource resource = rs.createResource(tx.getHbServer().createURI());
            ViewBase viewBase = createView("", deep, wide);
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
            Resource resource = rs.createResource(tx.getHbServer().createURI(id));
            resource.load(null);
            Assert.assertEquals(1, resource.getContents().size());
            return (ViewBase) resource.getContents().get(0);
        });
        Assert.assertEquals("", view2.getElementName());
        long afterLoad = System.currentTimeMillis();
        System.out.println("Created " + count + " objects");
        System.out.println("Inserted in " + (afterInsert - start) / 1000 + "s. " + count * 1000 / (afterInsert - start) + " object/s.");
        System.out.println("Loaded in " + (afterLoad - afterInsert) / 1000 + "s. " + count * 1000 / (afterLoad - afterInsert) + " object/s.");
    }

    @Test
    public void fullTest() throws Exception {
        long start = System.currentTimeMillis();
        for (int i = 0; i < nGroups; ++i) {
            int index = i;
            hbServer.inTransaction(false, tx -> {
                Group group = TestFactory.eINSTANCE.createGroup();
                String name = "group_" + index;
                group.setName(name);
                ResourceSet resourceSet = tx.getResourceSet();
                Resource groupResource = resourceSet.createResource(hbServer.createURI());
                groupResource.getContents().add(group);
                groupResource.save(null);
                Long groupId = hbServer.getId(group);
                groupIds.add(groupId);
                return null;
            });
        }
        long created1 = System.currentTimeMillis();
        for (int i = 0; i < nUsers; ++i) {
            int index = i;
            hbServer.inTransaction(false, tx -> {
                Random rand = new Random();
                Long groupId = groupIds.get(rand.nextInt(groupIds.size()));
                ResourceSet resourceSet = tx.getResourceSet();
                Resource groupResource = resourceSet.createResource(hbServer.createURI(groupId));
                groupResource.load(null);
                Group group = (Group) groupResource.getContents().get(0);
                User user = TestFactory.eINSTANCE.createUser();
                String name = "User_" + index;
                user.setName(name);
                user.setGroup(group);
                Resource userResource = resourceSet.createResource(hbServer.createURI());
                userResource.getContents().add(user);
                userResource.save(null);
                Long userId = hbServer.getId(user);
                userIds.add(userId);
                return null;
            });
        }
        long created2 = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        AtomicInteger eCount = new AtomicInteger(0);
        for (int i = 0; i < nThreads; ++i) {
            final int index = i;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Random rand = new Random();
                    for (int j = 0; j < nUpdates; ++j) {
                        String name = "User_" + index + "_" + j;
                        Long groupId = groupIds.get(rand.nextInt(groupIds.size()));
                        Long userId = userIds.get(rand.nextInt(userIds.size()));
                        try {
                            hbServer.inTransaction(false, tx -> {
                                ResourceSet resourceSet = tx.getResourceSet();
                                Resource groupResource = resourceSet.createResource(hbServer.createURI(groupId));
                                groupResource.load(null);
                                Group group = (Group) groupResource.getContents().get(0);
                                Resource userResource = resourceSet.createResource(hbServer.createURI(userId));
                                userResource.load(null);
                                User user = (User) userResource.getContents().get(0);
                                user.setName(name);
                                user.setGroup(group);
                                userResource.save(null);
                                return null;
                            });
                        } catch (Throwable e) {
                            System.out.println(e.getMessage());
                            eCount.incrementAndGet();
                        }
                    }
                }
            });
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long finish = System.currentTimeMillis();
        System.out.println("Created " + nGroups + " groups in " + (created1 - start) / 1000 + " sec");
        System.out.println("Created " + nUsers + " users  in " + (created2 - created1) / 1000 + " sec");
        System.out.println("Updated " + (nUpdates * nThreads) + " users in " + nThreads + " threads in " + (finish - created2) / 1000 + " sec. " + (nUpdates * nThreads) * 1000 / (finish - created2) + " object/s");
        System.out.println("Errors found: " + eCount.get());
        Assert.assertEquals(0, eCount.get());
    }
}
