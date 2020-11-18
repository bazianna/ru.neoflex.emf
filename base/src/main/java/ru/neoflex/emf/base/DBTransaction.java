package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DBTransaction implements AutoCloseable, Serializable {
    protected final Session session;
    protected transient String message = "";
    protected transient String author;
    protected transient String email;
    private transient boolean readOnly;
    private transient DBServer dbServer;
    private transient ResourceSet resourceSet;
    private Transaction tx;

    public DBTransaction(boolean readOnly, DBServer dbServer) {
        this.readOnly = readOnly;
        this.dbServer = dbServer;
        session = getDbServer().getSessionFactory().openSession();
    }

    public void close() {
        session.close();
    }

    protected DBObject get(Long id) {
        return session.get(DBObject.class, id);
    }

    protected Stream<DBObject> findAll() {
        return session.createQuery("select o from DBObject o", DBObject.class).getResultStream();
    }

    protected Stream<DBObject> findByClass(String classUri) {
        return session.createQuery("select o from DBObject o where o.classUri = :classUri", DBObject.class)
                .setParameter("classUri", classUri)
                .getResultStream();
    }

    protected Stream<DBObject> findByClassAndQName(String classUri, String qName) {
        return session.createQuery("select o from DBObject o where o.classUri = :classUri and o.qName = :qName", DBObject.class)
                .setParameter("classUri", classUri)
                .setParameter("qName", qName)
                .getResultStream();
    }

    protected Stream<DBObject> findReferencedTo(Long id) {
        return session.createQuery("select o from DBObject o join o.references r where r = :id", DBObject.class)
                .setParameter("id", id)
                .getResultStream();
    }

    protected void deleteRecursive(DBObject dbObject) {
        for (DBReference r: dbObject.getReferences()) {
            if (r.getContainment()) {
                deleteRecursive(r.getDbObject());
            }
        }
        session.delete(dbObject);
    }

    public boolean truncate() {
        session.createQuery("select r from DBObject r", DBObject.class).getResultStream().forEach(session::delete);
        return true;
    }

    public void begin() {
        if (!isReadOnly()) {
            tx = session.beginTransaction();
        }
    }

    public void commit() {
        if (tx != null) {
            tx.commit();
            tx = null;
        }
    }

    protected DBObject getOrThrow(Long id) {
        DBObject dbObject = get(id);
        if (dbObject == null) {
            throw new IllegalArgumentException("Object not found: " + id);
        }
        return dbObject;
    }

    private DBObject saveEObject(DBResource resource, DBObject dbObject, EObject eObject) {
        if (dbObject == null) {
            dbObject = new DBObject();
            dbObject.setVersion(0);
        }
        dbObject.setVersion(dbObject.getVersion() + 1);
        dbObject.setClassUri(EcoreUtil.getURI(eObject.eClass()).toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            List<AbstractMap.SimpleEntry<EAttribute, List>> attrs = eObject.eClass().getEAllAttributes().stream()
                    .filter(sf -> !sf.isDerived() && !sf.isTransient() && eObject.eIsSet(sf))
                    .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                            sf.isMany() ? (List)eObject.eGet(sf) : Arrays.asList(eObject.eGet(sf))))
                    .collect(Collectors.toList());
            int count = attrs.stream().map(entry -> entry.getValue().size()).reduce(0, Integer::sum);
            oos.writeInt(count);
            for (AbstractMap.SimpleEntry<EAttribute, List> attr: attrs) {
                EDataType eDataType = attr.getKey().getEAttributeType();
                String feature = attr.getKey().getName();
                for (Object value: attr.getValue()) {
                    oos.writeUTF(feature);
                    oos.writeUTF(EcoreUtil.convertToString(eDataType, value));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dbObject.setImage(baos.toByteArray());
        List<AbstractMap.SimpleEntry<EReference, List<EObject>>> refs = eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List<EObject>)eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))))
                .collect(Collectors.toList());
        Set<DBReference> toDeleteC = dbObject.getReferences().stream().filter(DBReference::getContainment).collect(Collectors.toSet());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref: refs) {
            if (ref.getKey().isContainment()) {
                String feature = ref.getKey().getName();
                for (EObject eObject2: ref.getValue()) {
                    EcoreUtil.resolveAll(eObject2);
                    if (eObject2.eIsProxy()) {
                        throw new RuntimeException("Can't resolve " + ((InternalEObject)eObject2).eProxyURI().toString());
                    }
                    DBResource resource2 = (DBResource) eObject2.eResource();
                    Long id2 = resource2.getID(eObject2);
                    DBObject dbObject2 = id2 != null ? getOrThrow(id2) : saveEObject(resource, null, eObject2);
                    int index = ref.getKey().isMany() ? ref.getValue().indexOf(eObject2) : -1;
                    DBReference dbReference = dbObject.getReferences().stream()
                            .filter(r -> r.getFeature().equals(feature) && r.getIndex() == index && r.getDbObject().getId().equals(id2))
                            .findFirst().orElse(null);
                    if (dbReference != null) {
                        toDeleteC.remove(dbReference);
                    }
                    else {
                        dbReference = new DBReference();
                        dbReference.setContainment(ref.getKey().isContainment());
                        dbObject.getReferences().add(dbReference);
                        dbReference.setFeature(feature);
                        dbReference.setIndex(index);
                        dbReference.setDbObject(dbObject2);
                    }
                }
            }
        }
        dbObject.getReferences().removeAll(toDeleteC);
        getSession().saveOrUpdate(dbObject);
        resource.setID(eObject, dbObject.getId());
        resource.setVersion(eObject, dbObject.getVersion());
        Set<DBReference> toDeleteNC = dbObject.getReferences().stream().filter(r->!r.getContainment()).collect(Collectors.toSet());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref: refs) {
            if (!ref.getKey().isContainment()) {
                String feature = ref.getKey().getName();
                for (EObject eObject2: ref.getValue()) {
                    int index = ref.getKey().isMany() ? ref.getValue().indexOf(eObject2) : -1;
                    if (eObject2.eResource() instanceof DBResource) {
                        DBResource resource2 = (DBResource) eObject2.eResource();
                        Long id2 = resource2.getID(eObject2);
                        DBObject dbObject2 = id2 != null ? getOrThrow(id2) : saveEObject(resource, null, eObject2);
                        DBReference dbReference = dbObject.getReferences().stream()
                                .filter(r -> r.getFeature().equals(feature) && r.getIndex() == index && r.getDbObject().getId().equals(id2))
                                .findFirst().orElse(null);
                        if (dbReference != null) {
                            toDeleteNC.remove(dbReference);
                        }
                        else {
                            dbReference = new DBReference();
                            dbReference.setContainment(ref.getKey().isContainment());
                            dbObject.getReferences().add(dbReference);
                            dbReference.setFeature(feature);
                            dbReference.setIndex(index);
                            dbReference.setDbObject(dbObject2);
                        }
                    }
                }
            }
        }
        dbObject.getReferences().removeAll(toDeleteNC);
        getSession().update(dbObject);
        return dbObject;
    }


    private EObject loadEObject(DBObject dbObject) {
        String classUri = dbObject.getClassUri();
        EClass eClass = (EClass) getResourceSet().getEObject(URI.createURI(classUri), false);
        EObject eObject = EcoreUtil.create(eClass);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(dbObject.getImage()))) {
            int count = ois.readInt();
            for (int i = 0; i < count; ++i) {
                String feature = ois.readUTF();
                String image = ois.readUTF();
                EStructuralFeature sf = eClass.getEStructuralFeature(feature);
                if (sf instanceof EAttribute) {
                    EAttribute eAttribute = (EAttribute) sf;
                    EDataType eDataType = eAttribute.getEAttributeType();
                    Object value = EcoreUtil.createFromString(eDataType, image);
                    if (sf.isMany()) {
                        ((List) eObject.eGet(sf)).add(value);
                    }
                    else {
                        eObject.eSet(sf, value);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<DBReference> references = new ArrayList<>(dbObject.getReferences());
        references.sort(Comparator.comparingInt(value -> value.getIndex()));
        for (DBReference dbReference: references) {
            EStructuralFeature sf = eClass.getEStructuralFeature(dbReference.getFeature());
            if (sf instanceof EReference) {
                DBObject dbRef = dbReference.getDbObject();
                EReference eReference = (EReference) sf;
                EObject refObject;
                if (eReference.isContainment()) {
                    refObject = loadEObject(dbRef);
                }
                else {
                    EClass refClass = (EClass) getResourceSet().getEObject(URI.createURI(dbRef.getClassUri()), false);
                    refObject = EcoreUtil.create(refClass);
                    if (dbRef.getProxy() != null) {
                        ((InternalEObject) refObject).eSetProxyURI(URI.createURI(dbRef.getProxy()));
                    }
                    else {
                        ((InternalEObject) refObject).eSetProxyURI(getDbServer().createURI(dbRef.getId()));
                    }
                }
                if (sf.isMany()) {
                    ((List)eObject.eGet(sf)).add(refObject);
                }
                else {
                    eObject.eSet(sf, refObject);
                }
            }
        }
        return eObject;
    }

    protected Resource createResource(ResourceSet rs, DBObject dbObject) {
        URI uri = getDbServer().createURI(dbObject.getId(), dbObject.getVersion());
        Resource resource = rs.createResource(uri);
        EObject eObject = loadEObject(dbObject);
        resource.getContents().add(eObject);
        return resource;
    }

    public Stream<Resource> findAll(ResourceSet rs) {
        return findAll()
                .map(dbResource -> createResource(rs, dbResource));
    }

    public Stream<Resource> findByClass(ResourceSet rs, EClass eClass) {
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClass(EcoreUtil.getURI(eClassDesc).trimQuery().toString())
                        .map(dbResource -> createResource(rs, dbResource)));
    }

    public Stream<Resource> findByClassAndQName(ResourceSet rs, EClass eClass, String qName) {
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClassAndQName(EcoreUtil.getURI(eClass).trimQuery().toString(), qName)
                        .map(dbResource -> createResource(rs, dbResource)));
    }

    public Stream<DBObject> findByClassAndQName(EClass eClass, String qName) {
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClassAndQName(EcoreUtil.getURI(eClass).trimQuery().toString(), qName));
    }

    public Stream<Resource> findReferencedTo(Resource resource) {
        return findReferencedTo(getDbServer().getId(resource.getURI()))
                .map(dbResource -> createResource(resource.getResourceSet(), dbResource));
    }

    public void save(DBResource resource) {
        EcoreUtil.resolveAll(resource);
        for (EObject eObject : resource.getContents()) {
            Diagnostic diagnostic = Diagnostician.INSTANCE.validate(eObject);
            if (diagnostic.getSeverity() == Diagnostic.ERROR ||
                    diagnostic.getSeverity() == Diagnostic.WARNING) {
                String message = getDiagnosticMessage(diagnostic);
                throw new RuntimeException(message);
            }
        }
        List<String> sameResources = resource.getContents().stream()
                .flatMap(eObject -> {
                    String qName = getDbServer().getQName(eObject);
                    return qName != null ?
                            findByClassAndQName(eObject.eClass(), qName)
                                    .filter(dbObject -> dbObject.getId().equals(resource.getID(eObject))) :
                            Stream.empty();
                })
                .map(dbObject -> String.valueOf(dbObject.getId()))
                .collect(Collectors.toList());
        if (sameResources.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate object names in resources (%s)",
                    String.join(", ", sameResources)));
        }
        Map<Long, EObject> oldECache = new HashMap<>();
        Map<Long, DBObject> oldDbCache = new HashMap<>();
        for (EObject eObject : resource.getContents()) {
            Long id = resource.getID(eObject);
            EObject oldObject = null;
            if (id != null) {
                Integer version = resource.getVersion(eObject);
                if (version == null) {
                    throw new IllegalArgumentException(String.format("Version for updated resource %s not defined", id));
                }
                DBObject dbObject = getOrThrow(id);
                if (!dbObject.getVersion().equals(version)) {
                    throw new IllegalArgumentException(String.format(
                            "Version (%d) for updated resource %d is not equals to the version in the DB (%d)",
                            version, id, dbObject.getVersion()));
                }
                oldDbCache.put(id, dbObject);
                oldObject = loadEObject(dbObject);
                oldECache.put(id, oldObject);
            }
            dbServer.getEvents().fireBeforeSave(oldObject, eObject);
        }
        for (EObject eObject : resource.getContents()) {
            Long id = resource.getID(eObject);
            DBObject dbObject = saveEObject(resource, oldDbCache.get(id), eObject);
            dbServer.getEvents().fireAfterSave(oldECache.get(id), eObject);
        }
        resource.getContents().stream()
                .filter(eObject -> eObject instanceof EPackage)
                .map(eObject -> (EPackage) eObject)
                .forEach(ePackage -> getDbServer().getPackageRegistry().put(ePackage.getNsURI(), ePackage));
    }

    public static String getDiagnosticMessage(Diagnostic diagnostic) {
        String message = diagnostic.getMessage();
        for (Iterator i = diagnostic.getChildren().iterator(); i.hasNext(); ) {
            Diagnostic childDiagnostic = (Diagnostic) i.next();
            message += "\n" + childDiagnostic.getMessage();
        }
        return message;
    }

    public void load(DBResource resource) {
        resource.unload();
        Long id = dbServer.getId(resource.getURI());
        DBObject dbObject = getOrThrow(id);
        EObject eObject = loadEObject(dbObject);
        dbServer.getEvents().fireAfterLoad(eObject);
    }

    public void delete(URI uri) {
        Long id = dbServer.getId(uri);
        if (id == null) {
            throw new IllegalArgumentException("Id for deleted object not defined");
        }
        Integer version = dbServer.getVersion(uri);
        if (version == null) {
            throw new IllegalArgumentException(String.format("Version for deleted object %s not defined", id));
        }
        DBObject dbObject = getOrThrow(id);
        Integer oldVersion = dbObject.getVersion();
        if (!version.equals(oldVersion)) {
            throw new IllegalArgumentException(String.format(
                    "Version (%d) for deleted object %d is not equals to the version in the DB (%d)",
                    version, id, oldVersion));
        }
        ResourceSet rs = getResourceSet();
        Resource oldResource = rs.createResource(uri);
        EObject eObject = loadEObject(dbObject);
        oldResource.getContents().add(eObject);
        dbServer.getEvents().fireBeforeDelete(eObject);
        deleteRecursive(dbObject);
        oldResource.getContents().stream()
                .filter(o -> o instanceof EPackage)
                .map(o -> (EPackage) o)
                .forEach(ePackage -> getDbServer().getPackageRegistry().remove(ePackage.getNsURI()));
    }

    public ResourceSet getResourceSet() {
        if (resourceSet == null) {
            resourceSet = createResourceSet();
        }
        return resourceSet;
    }

    private ResourceSet createResourceSet() {
        ResourceSetImpl result = new ResourceSetImpl();
        result.setPackageRegistry(getDbServer().getPackageRegistry());
        result.setURIResourceMap(new HashMap<>());
        result.getResourceFactoryRegistry()
                .getProtocolToFactoryMap()
                .put(dbServer.getScheme(), new ResourceFactoryImpl() {
                    @Override
                    public Resource createResource(URI uri) {
                        return dbServer.createResource(uri);
                    }
                });
        result.getURIConverter()
                .getURIHandlers()
                .add(0, new DBHandler(this));
        return result;
    }

    public DBServer getDbServer() {
        return dbServer;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getTenantId() {
        return getDbServer().getTenantId();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void rollback() {
        if (tx != null) {
            tx.rollback();
            tx = null;
        }
    }

    public Session getSession() {
        return session;
    }
}
