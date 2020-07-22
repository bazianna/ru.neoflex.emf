package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DBTransaction implements AutoCloseable, Serializable {
    private transient boolean readOnly;
    private transient DBServer dbServer;
    private String tenantId;

    protected abstract DBResource get(String id);
    protected abstract Stream<DBResource> findAll();
    protected abstract Stream<DBResource> findByClass(String classUri);
    protected abstract Stream<DBResource> findByClassAndQName(String classUri, String qName);
    protected abstract Stream<DBResource> findReferencedTo(String id);
    protected abstract void insert(DBResource dbResource);
    protected abstract void update(String id, DBResource dbResource);
    protected abstract void delete(String id);
    public void begin() {}
    public void commit() {}
    public void rollback() {}

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
        Set<String> names = resource.getContents().stream().map(eObject ->
            EcoreUtil.getURI(eObject.eClass()).toString() + ":" + getDbServer().getQName(eObject)
        ).collect(Collectors.toSet());
        dbResource.setNames(names);
        Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(resource);
        Set<String> references = xrs.keySet().stream()
                .map(eObject -> getDbServer().getId(EcoreUtil.getURI(eObject))).collect(Collectors.toSet());
        dbResource.setReferences(references);
        return dbResource;
    }

    public DBTransaction() {
    }

    public DBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        this.readOnly = readOnly;
        this.dbServer = dbServer;
        this.tenantId = tenantId;
    }

    public Stream<Resource> findAll(ResourceSet rs) {
        return findAll()
                .map(dbResource -> createResource(rs, dbResource));
    }

    public Stream<Resource> findByClass(ResourceSet rs, EClass eClass) {
        return findByClass(EcoreUtil.getURI(eClass).toString())
                .map(dbResource -> createResource(rs, dbResource));
    }

    public Stream<Resource> findByClassAndQName(ResourceSet rs, EClass eClass, String qName) {
        return findByClassAndQName(EcoreUtil.getURI(eClass).toString(), qName)
                .map(dbResource -> createResource(rs, dbResource));
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
                        "Version (%d) for updated resource %s is not equals to the version in the DB (%d)",
                        version, id, oldVersion));
            }
            load(oldDbResource, oldResource);
        }
        dbServer.getEvents().fireBeforeSave(oldResource, resource);
        DBResource newDbResource = null;
        if (id == null) {
            newDbResource = createDBResource(resource, id, version);
            insert(newDbResource);
        }
        else {
            newDbResource = fillDbResource(resource, oldDbResource);
            update(id, newDbResource);
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
                    "Version (%d) for deleted object %s is not equals to the version in the DB (%d)",
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
        resourceSet.getPackageRegistry()
                .put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        for (EPackage ePackage : dbServer.getPackages()) {
            resourceSet.getPackageRegistry()
                    .put(ePackage.getNsURI(), ePackage);
        }
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

    @Override
    public void close() throws Exception {
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void setDbServer(DBServer dbServer) {
        this.dbServer = dbServer;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
