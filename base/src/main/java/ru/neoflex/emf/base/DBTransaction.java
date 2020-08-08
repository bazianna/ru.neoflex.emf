package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DBTransaction implements AutoCloseable, Serializable {
    protected transient String message = "";
    protected transient String author;
    protected transient String email;
    private transient boolean readOnly;
    private transient DBServer dbServer;

    public DBTransaction() {
    }

    public DBTransaction(boolean readOnly, DBServer dbServer) {
        this.readOnly = readOnly;
        this.dbServer = dbServer;
    }

    protected abstract DBResource get(String id);

    protected abstract Stream<DBResource> findAll();

    protected abstract Stream<DBResource> findByClass(String classUri);

    protected abstract Stream<DBResource> findByClassAndQName(String classUri, String qName);

    protected abstract Stream<DBResource> findReferencedTo(String id);

    protected abstract void insert(DBResource dbResource);

    protected abstract void update(DBResource dbResource);

    protected abstract void delete(String id);

    public void begin() {
    }

    public void commit() {
    }

    public void rollback() {
    }

    protected void loadImage(DBResource dbResource, Resource resource) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dbResource.getImage());
        try {
            resource.load(inputStream, null);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Can't load %s", dbResource.getId()));
        }
    }

    protected void load(DBResource dbResource, Resource resource) {
        loadImage(dbResource, resource);
        URI uri = getDbServer().createURI(dbResource.getId(), dbResource.getVersion());
        resource.setURI(uri);
    }

    protected Resource createResource(ResourceSet rs, DBResource dbResource) {
        URI uri = getDbServer().createURI(dbResource.getId(), dbResource.getVersion());
        Resource resource = rs.createResource(uri);
        loadImage(dbResource, resource);
        return resource;
    }

    protected DBResource createDBResource(Resource resource, String id, String version) {
        DBResource dbResource = new DBResource();
        dbResource.setId(id);
        dbResource.setVersion(version);
        return fillDbResource(resource, dbResource);
    }

    private DBResource fillDbResource(Resource resource, DBResource dbResource) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.save(outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dbResource.setImage(outputStream.toByteArray());
        List<DBObject> dbObjects = resource.getContents().stream().map(eObject -> {
                    DBObject dbObject = new DBObject();
                    dbObject.setClassUri(EcoreUtil.getURI(eObject.eClass()).toString());
                    dbObject.setQName(getDbServer().getQName(eObject));
                    return dbObject;
                }
        ).collect(Collectors.toList());
        dbResource.setDbObjects(dbObjects);
        Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(resource);
        Set<String> references = xrs.keySet().stream()
                .map(eObject -> getDbServer().getId(EcoreUtil.getURI(eObject)))
                .filter(s -> s != null).collect(Collectors.toSet());
        dbResource.setReferences(references);
        return dbResource;
    }

    public Stream<Resource> findAll(ResourceSet rs) {
        return findAll()
                .map(dbResource -> createResource(rs, dbResource));
    }

    public Stream<Resource> findByClass(ResourceSet rs, EClass eClass) {
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClass(EcoreUtil.getURI(eClassDesc).toString())
                        .map(dbResource -> createResource(rs, dbResource)));
    }

    public Stream<Resource> findByClassAndQName(ResourceSet rs, EClass eClass, String qName) {
        return getDbServer().getConcreteDescendants(eClass).stream()
                .flatMap(eClassDesc -> findByClassAndQName(EcoreUtil.getURI(eClass).toString(), qName)
                        .map(dbResource -> createResource(rs, dbResource)));
    }

    public Stream<Resource> findReferencedTo(Resource resource) {
        return findReferencedTo(getDbServer().getId(resource.getURI()))
                .map(dbResource -> createResource(resource.getResourceSet(), dbResource));
    }

    public void save(Resource resource) {
        String id = dbServer.getId(resource.getURI());
        String version = dbServer.getVersion(resource.getURI());
        ResourceSet rs = createResourceSet();
        Resource oldResource = rs.createResource(resource.getURI());
        DBResource oldDbResource = null;
        if (id != null) {
            if (version == null) {
                throw new IllegalArgumentException(String.format("Version for updated resource %s not defined", id));
            }
            oldDbResource = get(id);
            String oldVersion = oldDbResource.getVersion();
            if (!version.equals(oldVersion)) {
                throw new IllegalArgumentException(String.format(
                        "Version (%s) for updated resource %s is not equals to the version in the DB (%s)",
                        version, id, oldVersion));
            }
            load(oldDbResource, oldResource);
        }
        dbServer.getEvents().fireBeforeSave(oldResource, resource);
        DBResource newDbResource;
        if (id == null) {
            newDbResource = createDBResource(resource, id, version);
            insert(newDbResource);
        } else {
            newDbResource = fillDbResource(resource, oldDbResource);
            update(newDbResource);
        }
        dbServer.getEvents().fireAfterSave(oldResource, resource);
        resource.setURI(getDbServer().createURI(newDbResource.getId(), newDbResource.getVersion()));
    }

    protected String getNextId() {
        return EcoreUtil.generateUUID();
    }

    public void load(Resource resource) {
        resource.unload();
        String id = dbServer.getId(resource.getURI());
        DBResource dbResource = get(id);
        load(dbResource, resource);
        dbServer.getEvents().fireAfterLoad(resource);
    }

    public void delete(URI uri) {
        String id = dbServer.getId(uri);
        if (id == null) {
            throw new IllegalArgumentException("Id for deleted object not defined");
        }
        String version = dbServer.getVersion(uri);
        if (version == null) {
            throw new IllegalArgumentException(String.format("Version for deleted object %s not defined", id));
        }
        DBResource dbResource = get(id);
        String oldVersion = dbResource.getVersion();
        if (!version.equals(oldVersion)) {
            throw new IllegalArgumentException(String.format(
                    "Version (%s) for deleted object %s is not equals to the version in the DB (%s)",
                    version, id, oldVersion));
        }
        ResourceSet rs = createResourceSet();
        Resource oldResource = rs.createResource(uri);
        load(dbResource, oldResource);
        dbServer.getEvents().fireBeforeDelete(oldResource);
        delete(id);
    }

    public ResourceSet createResourceSet() {
        ResourceSetImpl resourceSet = new ResourceSetImpl();
        EPackage.Registry registry = getDbServer().getPackageRegistry();
        resourceSet.setPackageRegistry(registry);
        resourceSet.setURIResourceMap(new HashMap<>());
        resourceSet.getResourceFactoryRegistry()
                .getProtocolToFactoryMap()
                .put(dbServer.getScheme(), new ResourceFactoryImpl() {
                    @Override
                    public Resource createResource(URI uri) {
                        return dbServer.createResource(uri);
                    }
                });
        resourceSet.getURIConverter()
                .getURIHandlers()
                .add(0, new DBHandler(this));
        return resourceSet;
    }

    public DBServer getDbServer() {
        return dbServer;
    }

    public void setDbServer(DBServer dbServer) {
        this.dbServer = dbServer;
    }

    @Override
    public void close() throws Exception {
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
}
