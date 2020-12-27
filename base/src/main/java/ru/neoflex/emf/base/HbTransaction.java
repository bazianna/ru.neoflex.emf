package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.neoflex.emf.base.HbServer.safeDecode;

public class HbTransaction implements AutoCloseable, Serializable {
    protected final Session session;
    private transient boolean readOnly;
    private final transient HbServer hbServer;
    private transient ResourceSet resourceSet;
    private Transaction tx;

    public HbTransaction(boolean readOnly, HbServer hbServer) {
        this.readOnly = readOnly;
        this.hbServer = hbServer;
        session = getHbServer().createSession();
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

    public HbResource createResource() {
        return (HbResource) getResourceSet().createResource(getHbServer().createURI());
    }

    public ResourceSet createResourceSet() {
        return hbServer.createResourceSet(this);
    }

    public HbServer getHbServer() {
        return hbServer;
    }

    public boolean isReadOnly() {
        return readOnly;
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

    public void close() {
        session.close();
    }

    protected DBObject get(Long id) {
        return session.get(DBObject.class, id);
    }

    protected void deleteRecursive(DBObject dbObject) {
        unlinkRecursive(dbObject);
        session.delete(dbObject);
    }

    protected void unlinkRecursive(DBObject dbObject) {
        List<DBObject> proxies = dbObject.getReferences().stream()
                .map(DBReference::getRefObject).filter(DBObject::isProxy).collect(Collectors.toList());
        if (dbObject.getReferences().size() > 0) {
            dbObject.getReferences().clear();
        }
        proxies.forEach(session::delete);
        dbObject.getContent().forEach(this::unlinkRecursive);
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
        Set<DBReference> toDeleteNC = new HashSet<>(dbObject.getReferences());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref : refsNC) {
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
                } else {
                    DBObject refDBObject;
                    if (id2 != null) {
                        refDBObject = getOrThrow(id2);
                    } else {
                        refDBObject = new DBObject();
                        refDBObject.setClassUri(EcoreUtil.getURI(eRefObject.eClass()).toString());
                        refDBObject.setProxy(EcoreUtil.getURI(eRefObject).toString());
                        session.persist(refDBObject);
                    }
                    createDBReference(dbObject, feature, index, refDBObject);
                }
            }
        }
        dbObject.getReferences().removeAll(toDeleteNC);
//        getSession().save(dbObject);
        toDeleteNC.forEach(dbReference -> {
            if (dbReference.getRefObject().isProxy()) {
                deleteRecursive(dbReference.getRefObject());
            }
        });
        eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && sf.isContainment() && eObject.eIsSet(sf))
                .flatMap(sf -> (sf.isMany() ? (List<EObject>) eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))).stream())
                .forEach(eRefObject -> {
                    Long id2 = hbServer.getId(eRefObject);
                    DBObject dbObject2 = getOrThrow(id2);
                    saveEObjectNonContainment(resource, dbObject2, eRefObject);
                });
    }

    private static void createDBReference(DBObject dbObject, String feature, int index, DBObject refDBObject) {
        DBReference dbReference;
        dbReference = new DBReference();
        dbObject.getReferences().add(dbReference);
        dbReference.setFeature(feature);
        dbReference.setIndex(index);
        dbReference.setRefObject(refDBObject);
    }

    private DBObject saveEObjectContainment(HbResource resource, DBObject dbObject, EObject eObject, DBObject container, String containingFeature, Integer containingIndex) {
        boolean needPersist = dbObject == null;
        if (dbObject == null) {
            dbObject = new DBObject();
        } else {
            Long version = resource.getTimeStamp();
            if (resource.getTimeStamp() <= 0) {
                throw new IllegalArgumentException(String.format("Version (%d) for updated resource %s not defined or invalid",
                        resource.getTimeStamp(), resource.getURI().toString()));
            }
            if (version < dbObject.getVersion()) {
                throw new IllegalArgumentException(String.format(
                        "Version (%d) for updated object %d less then the version in the DB (%d)",
                        version, dbObject.getId(), dbObject.getVersion()));
            }
        }
        dbObject.setVersion(resource.getTimeStamp());
        dbObject.setClassUri(EcoreUtil.getURI(eObject.eClass()).toString());
        dbObject.setContainer(container);
        dbObject.setFeature(containingFeature);
        dbObject.setIndex(containingIndex);

        List<AbstractMap.SimpleEntry<EAttribute, List<Object>>> attrs = eObject.eClass().getEAllAttributes().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List<Object>) eObject.eGet(sf) : Collections.singletonList(eObject.eGet(sf))))
                .collect(Collectors.toList());

        Set<DBAttribute> toDeleteA = new HashSet<>(dbObject.getAttributes());
        EAttribute qNameSF = getHbServer().getQNameSF(eObject.eClass());
        for (AbstractMap.SimpleEntry<EAttribute, List<Object>> attr : attrs) {
            if (attr.getKey() != qNameSF && !getHbServer().isIndexed(attr.getKey())) {
                continue;
            }
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
                } else {
                    dbAttribute = new DBAttribute();
                    dbObject.getAttributes().add(dbAttribute);
                    dbAttribute.setFeature(feature);
                    dbAttribute.setIndex(index);
                    dbAttribute.setValue(value);
                }
            }
        }
        dbObject.getAttributes().removeAll(toDeleteA);

        LinkedHashMap<String, List<String>> entriesMap = new LinkedHashMap<>();
        attrs.forEach(entry -> {
            EDataType eDataType = entry.getKey().getEAttributeType();
            String feature = entry.getKey().getName();
            entry.getValue().forEach(value ->
                    entriesMap.computeIfAbsent(feature, s -> new ArrayList<>()).add(EcoreUtil.convertToString(eDataType, value)));
        });
        dbObject.setAttributesMap(entriesMap);

        if (needPersist) {
            getSession().persist(dbObject);
            hbServer.setId(eObject, dbObject.getId());
        }

        List<AbstractMap.SimpleEntry<EReference, List<EObject>>> refsC = eObject.eClass().getEAllReferences().stream()
                .filter(sf -> !sf.isDerived() && !sf.isTransient() && !sf.isContainer() && sf.isContainment() && eObject.eIsSet(sf))
                .map(sf -> new AbstractMap.SimpleEntry<>(sf,
                        sf.isMany() ? (List<EObject>) eObject.eGet(sf) : Collections.singletonList((EObject) eObject.eGet(sf))))
                .collect(Collectors.toList());
        Set<DBObject> toDeleteC = new HashSet<>(dbObject.getContent());
        for (AbstractMap.SimpleEntry<EReference, List<EObject>> ref : refsC) {
            EReference eReference = ref.getKey();
            List<EObject> eObjects = ref.getValue();
            String feature = eReference.getName();
            for (int index = 0; index < eObjects.size(); ++index) {
                EObject containedEObject = eObjects.get(index);
                if (containedEObject.eIsProxy()) {
                    throw new RuntimeException("Can't resolve " + ((InternalEObject) containedEObject).eProxyURI().toString());
                }
                DBObject containedDBObject = null;
                Long containedId = hbServer.getId(containedEObject);
                if (containedId != null) {
                    containedDBObject = getOrThrow(containedId);
                } else {
                    if (!eReference.getEKeys().isEmpty()) {
                        containedDBObject = toDeleteC.stream().filter(o -> {
                            for (EAttribute a : eReference.getEKeys()) {
                                List<String> dbKeys = o.getAttributesMap().entrySet().stream()
                                        .filter(entry -> Objects.equals(entry.getKey(), a.getName()))
                                        .flatMap(entry -> entry.getValue().stream())
                                        .collect(Collectors.toList());
                                List<String> eKeys = (a.isMany() ?
                                        (List<Object>) containedEObject.eGet(a) :
                                        Collections.singletonList(containedEObject.eGet(a))).stream()
                                        .map(v -> v == null ? null : EcoreUtil.convertToString(a.getEAttributeType(), v))
                                        .collect(Collectors.toList());
                                if (dbKeys.size() != eKeys.size()) {
                                    return false;
                                }
                                for (int i = 0; i < dbKeys.size(); ++i) {
                                    if (!Objects.equals(dbKeys.get(i), eKeys.get(i))) return false;
                                }
                            }
                            return true;
                        }).findFirst().orElse(null);
                    }
                }
                if (containedDBObject != null) {
                    if (!toDeleteC.remove(containedDBObject)) {
                        dbObject.getContent().add(containedDBObject);
                    }
                    hbServer.setId(containedEObject, containedDBObject.getId());
                }
                saveEObjectContainment(resource, containedDBObject, containedEObject, dbObject, feature, index);
            }
        }
        dbObject.getContent().removeAll(toDeleteC);
        toDeleteC.forEach(this::deleteRecursive);
        return dbObject;
    }

    private URI getProxyURI(DBObject dbObject) {
        DBObject root = getRootContainer(dbObject);
//        DBObject root = dbObject;
        return getHbServer().createURI(root.getId())
                .appendFragment(String.valueOf(dbObject.getId()));
    }

    private EObject loadEObject(HbResource resource, DBObject dbObject, Map<String, Object> options) {
        if (options != null && (Boolean) options.getOrDefault(HbHandler.OPTION_GET_ROOT_CONTAINER, Boolean.FALSE)) {
            dbObject = getRootContainer(dbObject);
        }
        String classUri = dbObject.getClassUri();
        EClass eClass = (EClass) resource.getResourceSet().getEObject(URI.createURI(classUri), false);
        Objects.requireNonNull(eClass, () -> String.format("Class not found %s", classUri));
        EObject eObject = EcoreUtil.create(eClass);

        for (Map.Entry<String, List<String>> entry: dbObject.getAttributesMap().entrySet()) {
            String feature = entry.getKey();
            EStructuralFeature sf = eClass.getEStructuralFeature(feature);
            if (sf instanceof EAttribute) {
                EAttribute eAttribute = (EAttribute) sf;
                EDataType eDataType = eAttribute.getEAttributeType();
                for (String image: entry.getValue()) {
                    Object value = EcoreUtil.createFromString(eDataType, image);
                    if (sf.isMany()) {
                        ((List<Object>) eObject.eGet(sf)).add(value);
                    } else {
                        eObject.eSet(sf, value);
                    }
                }
            }
        }

        List<DBReference> references = new ArrayList<>(dbObject.getReferences());
        references.sort(Comparator.comparingInt(DBReference::getIndex));
        for (DBReference dbReference : references) {
            EStructuralFeature sf = eClass.getEStructuralFeature(dbReference.getFeature());
            if (sf instanceof EReference) {
                DBObject dbRef = dbReference.getRefObject();
                EReference eReference = (EReference) sf;
                EObject refObject;
                if (!eReference.isContainment()) {
                    EClass refClass = (EClass) getResourceSet().getEObject(URI.createURI(dbRef.getClassUri()), false);
                    refObject = EcoreUtil.create(refClass);
                    if (dbRef.getProxy() != null) {
                        ((InternalEObject) refObject).eSetProxyURI(URI.createURI(dbRef.getProxy()));
                    } else {
                        ((InternalEObject) refObject).eSetProxyURI(getProxyURI(dbRef));
                    }
                    if (sf.isMany()) {
                        ((List) eObject.eGet(sf)).add(refObject);
                    } else {
                        eObject.eSet(sf, refObject);
                    }
                }
            }
        }

        List<DBObject> content = new ArrayList<>(dbObject.getContent());
        content.sort(Comparator.comparingInt(DBObject::getIndex));
        for (DBObject containedDBObject : content) {
            EStructuralFeature sf = eClass.getEStructuralFeature(containedDBObject.getFeature());
            if (sf instanceof EReference) {
                EReference eReference = (EReference) sf;
                if (eReference.isContainment()) {
                    EObject refObject = loadEObject(resource, containedDBObject, null);
                    if (sf.isMany()) {
                        ((List) eObject.eGet(sf)).add(refObject);
                    } else {
                        eObject.eSet(sf, refObject);
                    }
                }
            }
        }
        hbServer.setId(eObject, dbObject.getId());
        return eObject;
    }

    protected Resource createResource(ResourceSet rs, DBObject dbObject, Map<String, Object> options) {
        URI uri = getHbServer().createURI(dbObject.getId());
        HbResource resource = (HbResource) rs.createResource(uri);
        EObject eObject = loadEObject(resource, dbObject, options);
        resource.getContents().add(eObject);
        resource.setTimeStamp(new Date().getTime());
        return resource;
    }

    public Stream<Resource> queryObjects(String sql) {
        return queryObjects(getResourceSet(), sql);
    }

    public Stream<Resource> queryObjects(ResourceSet rs, String sql) {
        return session.createQuery(sql, DBObject.class).getResultStream()
                .collect(Collectors.toList()).stream().map(dbResource -> createResource(rs, dbResource, null));
    }

    private DBObject getRootContainer(DBObject dbObject) {
        while (true) {
            DBObject parent = dbObject.getContainer();
            if (parent == null) {
                return dbObject;
            }
            dbObject = parent;
        }
    }

    public void save(HbResource resource) {
        List<EObject> contents = new ArrayList<>(resource.getContents());
        EcoreUtil.resolveAll(resource);
        resource.setTimeStamp(new Date().getTime());
        Map<Long, DBObject> oldDbCache = new HashMap<>();
        HbResource oldResource = (HbResource) getResourceSet().createResource(resource.getURI());
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            EObject oldObject = null;
            if (id != null) {
                DBObject dbObject = getOrThrow(id);
                oldDbCache.put(id, dbObject);
                oldObject = loadEObject(oldResource, dbObject, null);
                oldResource.getContents().add(oldObject);
            }
            hbServer.getEvents().fireBeforeSave(oldObject, eObject);
        }
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
                    EAttribute sf = hbServer.getQNameSF(eObject.eClass());
                    return sf != null ?
                            getHbServer().findBy(resource.getResourceSet(), eObject.eClass(), sf,
                                    EcoreUtil.convertToString(sf.getEAttributeType(), eObject.eGet(sf)))
                                    .getContents().stream().map(hbServer::getId)
                                    .filter(id -> id != null && !id.equals(hbServer.getId(eObject))) :
                            Stream.empty();
                })
                .map(String::valueOf)
                .collect(Collectors.toList());
        if (sameResources.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate object names in resources (%s)",
                    String.join(", ", sameResources)));
        }
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            DBObject dbObject = saveEObjectContainment(resource, oldDbCache.get(id), eObject, null, null, null);
            oldDbCache.put(dbObject.getId(), dbObject);
        }
        for (EObject eObject : contents) {
            Long id = hbServer.getId(eObject);
            saveEObjectNonContainment(resource, oldDbCache.get(id), eObject);
            hbServer.getEvents().fireAfterSave(oldResource.getEObjectByID(String.valueOf(id)), eObject);
        }
        contents.stream()
                .filter(eObject -> eObject instanceof EPackage)
                .map(eObject -> (EPackage) eObject)
                .forEach(ePackage -> getHbServer().getPackageRegistry().put(ePackage.getNsURI(), ePackage));
        fixResourceURI(resource);
    }

    public static String getDiagnosticMessage(Diagnostic diagnostic) {
        StringBuilder message = new StringBuilder(diagnostic.getMessage());
        for (Diagnostic childDiagnostic : diagnostic.getChildren()) {
            message.append("\n").append(childDiagnostic.getMessage());
        }
        return message.toString();
    }

    public void load(HbResource resource, Map<String, Object> options) {
        List<Long> ids = resource.getContents().stream().map(hbServer::getId).filter(Objects::nonNull).collect(Collectors.toList());
        resource.unload();
        resource.setTimeStamp(new Date().getTime());
        Long id = hbServer.getId(resource.getURI());
        if (id != null) {
            loadById(resource, id, options);
        } else {
            String query = resource.getURI().query();
            if (query != null) {
                for (String s : query.split("[&]")) {
                    String[] parts = s.split("[=]", 2);
                    if (parts.length == 2 && parts[0].startsWith("query")) {
                        String sql = safeDecode(parts[1]);
                        loadByQuery(resource, sql, options);
                    }
                }
            } else {
                // reload
                ids.forEach(oid -> {
                    loadById(resource, id, options);
                });
            }
        }
        fixResourceURI(resource);
    }

    private void fixResourceURI(HbResource resource) {
        if (resource.getContents().size() == 1) {
            EObject eObject = resource.getContents().get(0);
            resource.setURI(getHbServer().createURI(getHbServer().getId(eObject)));
        }
    }

    private void loadById(HbResource resource, Long id, Map<String, Object> options) {
        DBObject dbObject = getOrThrow(id);
        EObject eObject = loadEObject(resource, dbObject, options);
        resource.getContents().add(eObject);
        hbServer.getEvents().fireAfterLoad(eObject);
    }

    private void loadByQuery(HbResource resource, String query, Map<String, Object> options) {
        Query<DBObject> sql = session.createQuery(query, DBObject.class);
        sql.getParameterMetadata().collectAllParameters().forEach(queryParameter -> {
            sql.setParameter(queryParameter.getName(), options.get(queryParameter.getName()));
        });
        sql.getResultStream()
                .map(dbObject -> loadEObject(resource, dbObject, options))
                .filter(HbServer.distinctByKey(this.hbServer::getId))
                .forEach(eObject -> {
                    resource.getContents().add(eObject);
                    hbServer.getEvents().fireAfterLoad(eObject);
                });
    }

    public void delete(URI uri) {
        Long id = hbServer.getId(uri);
        if (id == null) {
            throw new IllegalArgumentException("Id for deleted object not defined");
        }
        Long version = hbServer.getVersion(uri);
        if (version == null) {
            throw new IllegalArgumentException(String.format("Version for deleted object %s not defined", id));
        }
        DBObject dbObject = getOrThrow(id);
        Long oldVersion = dbObject.getVersion();
        if (version < oldVersion) {
            throw new IllegalArgumentException(String.format(
                    "Version (%d) for deleted object %d is less then the version in the DB (%d)",
                    version, id, oldVersion));
        }
        ResourceSet rs = getResourceSet();
        HbResource oldResource = (HbResource) rs.createResource(uri);
        EObject eObject = loadEObject(oldResource, dbObject, null);
        oldResource.getContents().add(eObject);
        hbServer.getEvents().fireBeforeDelete(eObject);
        deleteRecursive(dbObject);
        oldResource.getContents().stream()
                .filter(o -> o instanceof EPackage)
                .map(o -> (EPackage) o)
                .forEach(ePackage -> getHbServer().getPackageRegistry().remove(ePackage.getNsURI()));
    }
}
