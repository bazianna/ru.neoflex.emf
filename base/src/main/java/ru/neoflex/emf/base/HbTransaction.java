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
import java.util.stream.StreamSupport;

public class HbTransaction implements AutoCloseable, Serializable {
    protected final Session session;
    protected transient String message = "";
    protected transient String author;
    protected transient String email;
    private transient boolean readOnly;
    private transient HbServer hbServer;
    private transient ResourceSet resourceSet;
    private Transaction tx;

    public HbTransaction(boolean readOnly, HbServer hbServer) {
        this.readOnly = readOnly;
        this.hbServer = hbServer;
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

    protected Stream<DBObject> findByClassAndFeature(String classUri, String feature, String value) {
        return session.createQuery("select o from DBObject o join o.attributes a where o.classUri = :classUri and a.feature = :feature and a.value = :value", DBObject.class)
                .setParameter("classUri", classUri)
                .setParameter("feature", feature)
                .setParameter("value", value)
                .getResultStream();
    }

    protected Stream<DBObject> findReferencedTo(Long id) {
        return session.createQuery("select o from DBObject o join o.references r where r.containment = false and r.refObject.id = :id", DBObject.class)
                .setParameter("id", id)
                .getResultStream();
    }

    protected void deleteRecursive(DBObject dbObject) {
        Set<String> deps = session.createQuery(
                "select o from DBObject o join o.references r " +
                        "where r.containment = false and r.refObject.id = :refdb_id"
                , DBObject.class).setParameter("refdb_id", dbObject.getId()).getResultStream()
                .map(o->String.valueOf(o.getId())).collect(Collectors.toSet());
        if (deps.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Can not delete Resource, referenced by [%s]", String.join(", ", deps)));
        }
        for (DBReference r: dbObject.getReferences()) {
            if (r.getContainment() || r.getRefObject().isProxy()) {
                deleteRecursive(r.getRefObject());
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

    private void saveEObjectNonContainment(HbResource resource, DBObject dbObject, EObject eObject) {
        List<AbstractMap.SimpleEntry<EReference, List<EObject>>> refsNC = eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && !sf.isContainment() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List<EObject>) eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))))
                .collect(Collectors.toList());
        Set<DBReference> toDeleteNC = dbObject.getReferences().stream().filter(r->!r.getContainment()).collect(Collectors.toSet());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref: refsNC) {
            String feature = ref.getKey().getName();
            for (int i = 0; i < ref.getValue().size(); ++i) {
                final int index = i;
                EObject eRefObject = ref.getValue().get(index);
                Long id2 = hbServer.getId(eRefObject);
                DBReference dbReference = dbObject.getReferences().stream()
                        .filter(r -> r.getFeature().equals(feature) && r.getIndex() == index && r.getRefObject().getId().equals(id2))
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
                    DBObject refDBObject;
                    if (id2 != null) {
                        refDBObject = getOrThrow(id2);
                    }
                    else {
                        refDBObject = new DBObject();
                        refDBObject.setClassUri(EcoreUtil.getURI(eRefObject.eClass()).toString());
                        refDBObject.setProxy(EcoreUtil.getURI(eRefObject).toString());
                        session.persist(refDBObject);
                    }
                    dbReference.setRefObject(refDBObject);
                }
            }
        }
        dbObject.getReferences().removeAll(toDeleteNC);
        getSession().save(dbObject);
        toDeleteNC.forEach(dbReference -> {
            if (dbReference.getRefObject().isProxy()) {
                deleteRecursive(dbReference.getRefObject());
            }
        });
        eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && sf.isContainment() && eObject.eIsSet(sf))
                .flatMap(sf -> (sf.isMany() ? (List<EObject>) eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))).stream())
                .forEach(eRefObject -> {
                    HbResource resource2 = (HbResource) eRefObject.eResource();
                    Long id2 = hbServer.getId(eRefObject);
                    DBObject dbObject2 = getOrThrow(id2);
                    saveEObjectNonContainment(resource, dbObject2, eRefObject);
                });
    }

    private DBObject saveEObjectContainment(HbResource resource, DBObject dbObject, EObject eObject) {
        if (dbObject == null) {
            dbObject = new DBObject();
            dbObject.setVersion(0);
        }
        dbObject.setVersion(dbObject.getVersion() + 1);
        dbObject.setClassUri(EcoreUtil.getURI(eObject.eClass()).toString());

        List<AbstractMap.SimpleEntry<EAttribute, List>> attrs = eObject.eClass().getEAllAttributes().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List) eObject.eGet(sf) : Arrays.asList(eObject.eGet(sf))))
                .collect(Collectors.toList());

        Set<DBAttribute> toDeleteA = dbObject.getAttributes().stream().collect(Collectors.toSet());
        for (AbstractMap.SimpleEntry<EAttribute, List> attr: attrs) {
            EDataType eDataType = attr.getKey().getEAttributeType();
            String feature = attr.getKey().getName();
            for (int index = 0; index < attr.getValue().size(); ++index) {
                Object valueObject = attr.getValue().get(index);
                String value = EcoreUtil.convertToString(eDataType, valueObject);
                int finalIndex = index;
                DBAttribute dbAttribute = dbObject.getAttributes().stream()
                        .filter(a -> a.getFeature().equals(feature) && a.getIndex() == finalIndex && Objects.equals(a.getValue(), value))
                        .findFirst().orElse(null);
                if (dbAttribute != null) {
                    toDeleteA.remove(dbAttribute);
                }
                else {
                    dbAttribute = new DBAttribute();
                    dbObject.getAttributes().add(dbAttribute);
                    dbAttribute.setFeature(feature);
                    dbAttribute.setIndex(index);
                    dbAttribute.setValue(value);
                }
            }
        }
        dbObject.getAttributes().removeAll(toDeleteA);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
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
        List<AbstractMap.SimpleEntry<EReference, List<EObject>>> refsC = eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && sf.isContainment() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List<EObject>) eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))))
                .collect(Collectors.toList());
        Set<DBReference> toDeleteC = dbObject.getReferences().stream().filter(DBReference::getContainment).collect(Collectors.toSet());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref: refsC) {
            String feature = ref.getKey().getName();
            for (EObject eObject2: ref.getValue()) {
                EcoreUtil.resolveAll(eObject2);
                if (eObject2.eIsProxy()) {
                    throw new RuntimeException("Can't resolve " + ((InternalEObject)eObject2).eProxyURI().toString());
                }
                Long id2 = hbServer.getId(eObject2);
                DBObject dbObject2 = id2 != null ? getOrThrow(id2) : saveEObjectContainment(resource, null, eObject2);
                int index = ref.getValue().indexOf(eObject2);
                DBReference dbReference = dbObject.getReferences().stream()
                        .filter(r -> r.getFeature().equals(feature) && r.getIndex() == index && r.getRefObject().getId().equals(id2))
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
                    dbReference.setRefObject(dbObject2);
                }
            }
        }
        dbObject.getReferences().removeAll(toDeleteC);
        getSession().saveOrUpdate(dbObject);
        toDeleteC.forEach(dbReference -> deleteRecursive(dbReference.getRefObject()));
        hbServer.setId(eObject, dbObject.getId());
        hbServer.setVersion(eObject, dbObject.getVersion());
        return dbObject;
    }


    private EObject loadEObject(HbResource resource, DBObject dbObject) {
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
                DBObject dbRef = dbReference.getRefObject();
                EReference eReference = (EReference) sf;
                EObject refObject;
                if (eReference.isContainment()) {
                    refObject = loadEObject(resource, dbRef);
                }
                else {
                    EClass refClass = (EClass) getResourceSet().getEObject(URI.createURI(dbRef.getClassUri()), false);
                    refObject = EcoreUtil.create(refClass);
                    if (dbRef.getProxy() != null) {
                        ((InternalEObject) refObject).eSetProxyURI(URI.createURI(dbRef.getProxy()));
                    }
                    else {
                        ((InternalEObject) refObject).eSetProxyURI(getDbServer().createURI(dbRef.getId()).appendFragment(String.valueOf(dbRef.getId())));
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
        hbServer.setId(eObject, dbObject.getId());
        hbServer.setVersion(eObject, dbObject.getVersion());
        return eObject;
    }

    protected Resource createResource(ResourceSet rs, DBObject dbObject) {
        URI uri = getDbServer().createURI(dbObject.getId(), dbObject.getVersion());
        HbResource resource = (HbResource) rs.createResource(uri);
        EObject eObject = loadEObject(resource, dbObject);
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
        EStructuralFeature sf = getDbServer().getQNameSF(eClass);
        if (sf == null) {
            return Stream.empty();
        }
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClassAndFeature(EcoreUtil.getURI(eClass).trimQuery().toString(), sf.getName(), qName)
                        .map(dbResource -> createResource(rs, dbResource)));
    }

    private DBObject getParent(DBObject dbObject) {
        return session.createQuery("select o from DBObject o join o.references r where r.refObject.id = :refdb_id", DBObject.class)
                .setParameter("refdb_id", dbObject.getId())
                .uniqueResult();
    }

    private DBObject getContainer(DBObject dbObject) {
        while (true) {
            DBObject parent = getParent(dbObject);
            if (parent == null) {
                return dbObject;
            }
            dbObject = parent;
        }
    }

    public Stream<Resource> findReferencedTo(Resource resource) {
        Set<Long> exclude = resource.getContents().stream().map(eObject -> getDbServer().getId(eObject))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Iterable<EObject> iterable = resource::getAllContents;
        Set<DBObject> topRefs = StreamSupport.stream(iterable.spliterator(), false)
                .map(eObject -> getDbServer().getId(eObject)).filter(Objects::nonNull)
                .flatMap(this::findReferencedTo)
                .collect(Collectors.toList()).stream()
                .map(this::getContainer).filter(dbObject -> !exclude.contains(dbObject.getId()))
                .collect(Collectors.toSet());
        return topRefs.stream().map(dbObject -> createResource(resource.getResourceSet(), dbObject));
    }

    public void save(HbResource resource) {
        List<EObject> contents = new ArrayList<>(resource.getContents());
        EcoreUtil.resolveAll(resource);
        for (EObject eObject : resource.getContents()) {
            Diagnostic diagnostic = Diagnostician.INSTANCE.validate(eObject);
            if (diagnostic.getSeverity() == Diagnostic.ERROR ||
                    diagnostic.getSeverity() == Diagnostic.WARNING) {
                String message = getDiagnosticMessage(diagnostic);
                throw new RuntimeException(message);
            }
        }
        List<String> sameResources = contents.stream()
                .flatMap(eObject -> {
                    String qName = getDbServer().getQName(eObject);
                    return qName != null ?
                            findByClassAndQName(eObject.eClass(), qName)
                                    .filter(dbObject -> !dbObject.getId().equals(hbServer.getId(eObject))) :
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
        HbResource oldResource = (HbResource) getResourceSet().createResource(resource.getURI());
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            EObject oldObject = null;
            if (id != null) {
                Integer version = hbServer.getVersion(eObject);
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
                oldObject = loadEObject(oldResource, dbObject);
                oldResource.getContents().add(oldObject);
                oldECache.put(id, oldObject);
            }
            hbServer.getEvents().fireBeforeSave(oldObject, eObject);
        }
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            DBObject dbObject = saveEObjectContainment(resource, oldDbCache.get(id), eObject);
            oldDbCache.put(dbObject.getId(), dbObject);
        }
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            saveEObjectNonContainment(resource, oldDbCache.get(id), eObject);
            hbServer.getEvents().fireAfterSave(oldECache.get(id), eObject);
        }
        contents.stream()
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

    public void load(HbResource resource) {
        resource.unload();
        Long id = hbServer.getId(resource.getURI());
        DBObject dbObject = getOrThrow(id);
        EObject eObject = loadEObject(resource, dbObject);
        resource.getContents().add(eObject);
        hbServer.getEvents().fireAfterLoad(eObject);
    }

    public void delete(URI uri) {
        Long id = hbServer.getId(uri);
        if (id == null) {
            throw new IllegalArgumentException("Id for deleted object not defined");
        }
        Integer version = hbServer.getVersion(uri);
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
        HbResource oldResource = (HbResource) rs.createResource(uri);
        EObject eObject = loadEObject(oldResource, dbObject);
        oldResource.getContents().add(eObject);
        hbServer.getEvents().fireBeforeDelete(eObject);
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

    public HbResource createResource(URI uri) {
        return (HbResource) getResourceSet().createResource(uri);
    }

    public ResourceSet createResourceSet() {
        ResourceSetImpl result = new ResourceSetImpl();
        result.setPackageRegistry(getDbServer().getPackageRegistry());
        result.setURIResourceMap(new HashMap<>());
        result.getResourceFactoryRegistry()
                .getProtocolToFactoryMap()
                .put(hbServer.getScheme(), new ResourceFactoryImpl() {
                    @Override
                    public Resource createResource(URI uri) {
                        return hbServer.createResource(uri);
                    }
                });
        result.getURIConverter()
                .getURIHandlers()
                .add(0, new HbHandler(this));
        return result;
    }

    public HbServer getDbServer() {
        return hbServer;
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
