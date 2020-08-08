package emfgit;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.gitdb.test.Group;
import ru.neoflex.emf.gitdb.test.TestFactory;
import ru.neoflex.emf.gitdb.test.TestPackage;
import ru.neoflex.emf.gitdb.test.User;

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
    public void shutDown() throws Exception {
        dbServer.close();
    }

    public EPackage createDynamicPackage() {
        // https://www.ibm.com/developerworks/library/os-eclipse-dynamicemf/
        /*
         * Instantiate EcoreFactory
         */
        EcoreFactory theCoreFactory = EcoreFactory.eINSTANCE;

        /*
         * Create EClass instance to model BookStore class
         */
        EClass bookStoreEClass = theCoreFactory.createEClass();
        bookStoreEClass.setName("BookStore");

        /*
         * Create EClass instance to model Book class
         */
        EClass bookEClass = theCoreFactory.createEClass();
        bookEClass.setName("Book");

        /*
         * Instantiate EPackage and provide unique URI
         * to identify this package
         */
        EPackage bookStoreEPackage = theCoreFactory.createEPackage();
        bookStoreEPackage.setName("BookStorePackage");
        bookStoreEPackage.setNsPrefix("bookStore");
        bookStoreEPackage.setNsURI("http:///com.ibm.dynamic.example.bookstore.ecore");

        /*
         * Instantiate EcorePackage
         */
        EcorePackage theCorePackage = EcorePackage.eINSTANCE;

        /*
         * Create attributes for BookStore class as specified in the model
         */
        EAttribute bookStoreName = theCoreFactory.createEAttribute();
        bookStoreName.setName("name");
        bookStoreName.setEType(theCorePackage.getEString());
        EAttribute bookStoreOwner = theCoreFactory.createEAttribute();
        bookStoreOwner.setName("owner");
        bookStoreOwner.setEType(theCorePackage.getEString());
        EAttribute bookStoreLocation = theCoreFactory.createEAttribute();
        bookStoreLocation.setName("location");
        bookStoreLocation.setEType(theCorePackage.getEString());
        EReference bookStore_Books = theCoreFactory.createEReference();
        bookStore_Books.setName("books");
        bookStore_Books.setEType(bookEClass);
        bookStore_Books.setUpperBound(EStructuralFeature.UNBOUNDED_MULTIPLICITY);
        bookStore_Books.setContainment(true);

        /*
         * Create attributes for Book class as defined in the model
         */
        EAttribute bookName = theCoreFactory.createEAttribute();
        bookName.setName("name");
        bookName.setEType(theCorePackage.getEString());
        EAttribute bookISBN = theCoreFactory.createEAttribute();
        bookISBN.setName("isbn");
        bookISBN.setEType(theCorePackage.getEInt());

        /*
         * Add owner, location and books attributes/references
         * to BookStore class
         */
        bookStoreEClass.getEStructuralFeatures().add(bookStoreName);
        bookStoreEClass.getEStructuralFeatures().add(bookStoreLocation);
        bookStoreEClass.getEStructuralFeatures().add(bookStore_Books);

        /*
         * Add name and isbn attributes to Book class
         */
        bookEClass.getEStructuralFeatures().add(bookName);
        bookEClass.getEStructuralFeatures().add(bookISBN);

        /*
         * Place BookStore and Book classes in bookStoreEPackage
         */
        bookStoreEPackage.getEClassifiers().add(bookStoreEClass);
        bookStoreEPackage.getEClassifiers().add(bookEClass);

        return bookStoreEPackage;
    }

    @Test
    public void testDynamic() throws Exception {
        EPackage ePackage = createDynamicPackage();
        dbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Resource resource = resourceSet.createResource(dbServer.createURI(""));
            resource.getContents().add(ePackage);
            resource.save(null);
            return null;
        });
        EPackage ePackage2 = dbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Resource resource = tx.findByClassAndQName(resourceSet, EcorePackage.Literals.EPACKAGE, ePackage.getNsURI())
                    .collect(Collectors.toList()).get(0);
            return (EPackage) resource.getContents().get(0);
        });
        Assert.assertEquals(ePackage.getNsPrefix(), ePackage2.getNsPrefix());
        Assert.assertEquals(2, ePackage2.getEClassifiers().size());
        dbServer.inTransaction(false, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            EClass bookStoreClass = (EClass) resourceSet.getEObject(URI.createURI("http:///com.ibm.dynamic.example.bookstore.ecore#//BookStore"), false);
            EObject bookStore = EcoreUtil.create(bookStoreClass);
            bookStore.eSet(bookStoreClass.getEStructuralFeature("name"), "My Book Store");
            Resource resource = resourceSet.createResource(dbServer.createURI(""));
            resource.getContents().add(bookStore);
            resource.save(null);
            return null;
        });
        List<EObject> bookStores = dbServer.inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            EClass bookStoreClass = (EClass) resourceSet.getEObject(URI.createURI("http:///com.ibm.dynamic.example.bookstore.ecore#//BookStore"), false);
            return tx.findByClass(resourceSet, bookStoreClass)
                    .flatMap(resource -> resource.getContents().stream())
                    .collect(Collectors.toList());
        });
        Assert.assertEquals(1, bookStores.size());
        EObject bookStore = bookStores.get(0);
        Assert.assertEquals("My Book Store", bookStore.eGet(bookStore.eClass().getEStructuralFeature("name")));
    }

    @Test
    public void createEMFObject() throws Exception {
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
        dbServer.inTransaction(true, (DBServer.TxFunction<Void>) tx -> {
            ResourceSet resourceSet = tx.createResourceSet();
            Assert.assertEquals(3, tx.findAll(resourceSet).count());
            Assert.assertEquals(2, tx.findByClass(resourceSet, TestPackage.Literals.USER).count());
            Assert.assertEquals(2, tx.findReferencedTo(group.eResource()).count());
            Assert.assertEquals(1, tx.findByClassAndQName(resourceSet, TestPackage.Literals.USER, "Simanihin").count());
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
