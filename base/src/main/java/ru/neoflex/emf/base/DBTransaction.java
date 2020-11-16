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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
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
        return session.createQuery("select r from DBObject r", DBObject.class).getResultStream();
    }

    protected Stream<DBObject> findByClass(String classUri) {
        return session.createQuery("select r from DBObject r where r.classUri = :classUri", DBObject.class)
                .setParameter("classUri", classUri)
                .getResultStream();
    }

    protected Stream<DBObject> findByClassAndQName(String classUri, String qName) {
        return session.createQuery("select r from DBObject r where r.classUri = :classUri and r.qName = :qName", DBObject.class)
                .setParameter("classUri", classUri)
                .setParameter("qName", qName)
                .getResultStream();
    }

    protected Stream<DBObject> findReferencedTo(Long id) {
        return session.createQuery("select r from DBObject r join r.references reference where reference = :id", DBObject.class)
                .setParameter("id", id)
                .getResultStream();
    }

    protected void insert(DBObject dbObject) {
        dbObject.setVersion(0);
        session.persist(dbObject);
    }

    protected void update(DBObject oldDbObject, DBObject dbObject) {
        oldDbObject.setVersion(1 + oldDbObject.getVersion());
        oldDbObject.setImage(dbObject.getImage());
        oldDbObject.setReferences(dbObject.getReferences());
        session.update(oldDbObject);
    }

    protected void delete(DBObject dbObject) {
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

    protected void loadImage(DBObject dbObject, Resource resource) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dbObject.getImage());
        try {
            resource.load(inputStream, null);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void load(DBObject dbObject, Resource resource) {
        loadImage(dbObject, resource);
        URI uri = getDbServer().createURI(dbObject.getId(), dbObject.getVersion());
        resource.setURI(uri);
    }

    protected Resource createResource(ResourceSet rs, DBObject dbObject) {
        URI uri = getDbServer().createURI(dbObject.getId(), dbObject.getVersion());
        Resource resource = rs.createResource(uri);
        loadImage(dbObject, resource);
        return resource;
    }

    protected DBObject createDBObject(Resource resource, Long id, Integer version) {
        DBObject dbResource = new DBObject();
        dbResource.setId(id);
        dbResource.setVersion(version);
        return fillDbResource(resource, dbResource);
    }

    protected DBObject fillDbResource(Resource resource, DBObject dbObject) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.save(outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dbObject.setImage(outputStream.toByteArray());
        fillIndexes(resource, dbObject);
        return dbObject;
    }

    protected void fillIndexes(Resource resource, DBObject dbObject) {
        List<DBObject> dbObjects = resource.getContents().stream().map(eObject -> {
                    DBObject result = new DBObject();
                    URI classUri = EcoreUtil.getURI(eObject.eClass()).trimQuery();
                    result.setClassUri(classUri.toString());
                    result.setqName(getDbServer().getQName(eObject));
                    return result;
                }
        ).collect(Collectors.toList());
        Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(resource);
        List<DBReference> references = xrs.keySet().stream()
                .map(eObject -> {
                    URI uri = EcoreUtil.getURI(eObject);
                    if (uri.isRelative()) {
                        uri = uri.resolve(resource.getURI());
                    }
                    else if (!getDbServer().canHandle(uri)) {
                        return null;
                    }
                    Long id = getDbServer().getId(uri);
                    if (id == null) {
                        return null;
                    }
                    return xrs.get(eObject).stream().map(setting -> {
                        DBReference dbReference = new DBReference();
                        EReference eReference = (EReference)setting.getEStructuralFeature();
                        dbReference.setContainment(eReference.isContainment());
                        dbReference.setFeature(eReference.getName());
                        List<EObject> l = eReference.isMany() ? (List)setting.getEObject().eGet(eReference) : Collections.singletonList(eObject);
                        dbReference.setIndex(l.indexOf(eObject));
                        return dbReference;
                    });
                })
                .filter(Objects::nonNull)
                .flatMap(s->s)
                .collect(Collectors.toList());
        dbObject.setReferences(references);
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

    public Stream<Resource> findReferencedTo(Resource resource) {
        return findReferencedTo(getDbServer().getId(resource.getURI()))
                .map(dbResource -> createResource(resource.getResourceSet(), dbResource));
    }

    public void save(Resource resource) {
        EcoreUtil.resolveAll(resource);
        for (EObject eObject: resource.getContents()) {
            Diagnostic diagnostic = Diagnostician.INSTANCE.validate(eObject);
            if (diagnostic.getSeverity() == Diagnostic.ERROR ||
                    diagnostic.getSeverity() == Diagnostic.WARNING) {
                String message = getDiagnosticMessage(diagnostic);
                throw new RuntimeException(message);
            }
        }
        Long id = dbServer.getId(resource.getURI());
        Integer version = dbServer.getVersion(resource.getURI());
        ResourceSet rs = getResourceSet();
        Resource oldResource = rs.createResource(resource.getURI());
        DBObject oldDbObject = null;
        if (id != null) {
            oldDbObject = get(id);
            if (oldDbObject != null) {
                if (version == null) {
                    throw new IllegalArgumentException(String.format("Version for updated resource %s not defined", id));
                }
                Integer oldVersion = oldDbObject.getVersion();
                if (!version.equals(oldVersion)) {
                    throw new IllegalArgumentException(String.format(
                            "Version (%s) for updated resource %s is not equals to the version in the DB (%s)",
                            version, id, oldVersion));
                }
                load(oldDbObject, oldResource);
                List<String> brokenRefs = findReferencedTo(oldResource).flatMap(referenced -> {
                    Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(referenced);
                    return xrs.entrySet().stream().filter(entry -> entry.getKey().eResource().getURI().equals(oldResource.getURI()))
                            .flatMap(entry -> entry.getValue().stream().map(setting -> {
                                EObject refObject = setting.getEObject();
                                EClass refClass = refObject.eClass();
                                String fragment = refObject.eResource().getURIFragment(refObject);
                                EObject newRefObject = resource.getEObject(fragment);
                                if (newRefObject == null || !newRefObject.eClass().equals(refClass)) {
                                    return entry.getKey().eResource().getURI().appendFragment(fragment);
                                }
                                return null;
                            }).filter(uri -> uri != null).map(uri->uri.toString()));
                }).collect(Collectors.toList());
                if (brokenRefs.size() > 0) {
                    throw new IllegalArgumentException(String.format(
                            "Broken references (%s)",
                            String.join(", ", brokenRefs)));
                }
            }
        }
        List<String> sameResources = resource.getContents().stream()
                .flatMap(eObject -> {
                    String qName = getDbServer().getQName(eObject);
                    return qName != null ? findByClassAndQName(rs, eObject.eClass(), qName) : Stream.empty();
                })
                .filter(sameName -> !getDbServer().getId(sameName.getURI()).equals(getDbServer().getId(resource.getURI())))
                .map(sameName -> String.valueOf(getDbServer().getId(sameName.getURI())))
                .collect(Collectors.toList());
        if (sameResources.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate object names in resources (%s)",
                    String.join(", ", sameResources)));
        }
        dbServer.getEvents().fireBeforeSave(oldResource, resource);
        DBObject newDbObject;
        if (oldDbObject == null) {
            resource.setURI(getDbServer().createURI(id));
            newDbObject = createDBObject(resource, id, version);
            insert(newDbObject);
        } else {
            newDbObject = createDBObject(resource, oldDbObject.getId(), oldDbObject.getVersion());
            update(oldDbObject, newDbObject);
        }
        dbServer.getEvents().fireAfterSave(oldResource, resource);
        resource.setURI(getDbServer().createURI(newDbObject.getId(), newDbObject.getVersion()));
        resource.getContents().stream()
                .filter(eObject -> eObject instanceof EPackage)
                .map(eObject -> (EPackage)eObject)
                .forEach(ePackage -> getDbServer().getPackageRegistry().put(ePackage.getNsURI(), ePackage));
    }

    public static String getDiagnosticMessage(Diagnostic diagnostic) {
        String message = diagnostic.getMessage();
        for (Iterator i = diagnostic.getChildren().iterator(); i.hasNext();) {
            Diagnostic childDiagnostic = (Diagnostic)i.next();
            message += "\n" + childDiagnostic.getMessage();
        }
        return message;
    }

    public void load(Resource resource) {
        resource.unload();
        Long id = dbServer.getId(resource.getURI());
        DBObject dbObject = getOrThrow(id);
        load(dbObject, resource);
        dbServer.getEvents().fireAfterLoad(resource);
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
                    "Version (%s) for deleted object %s is not equals to the version in the DB (%s)",
                    version, id, oldVersion));
        }
        ResourceSet rs = getResourceSet();
        Resource oldResource = rs.createResource(uri);
        load(dbObject, oldResource);
        List<String> refs = findReferencedTo(oldResource)
                .map(resource -> String.valueOf(getDbServer().getId(resource.getURI())))
                .collect(Collectors.toList());
        if (refs.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Can't delete referenced resource (%s)",
                    String.join(", ", refs)));
        }
        dbServer.getEvents().fireBeforeDelete(oldResource);
        delete(dbObject);
        oldResource.getContents().stream()
                .filter(eObject -> eObject instanceof EPackage)
                .map(eObject -> (EPackage)eObject)
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

    public void setDbServer(DBServer dbServer) {
        this.dbServer = dbServer;
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
