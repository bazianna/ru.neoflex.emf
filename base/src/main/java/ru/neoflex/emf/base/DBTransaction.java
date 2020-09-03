package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
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
    private transient ResourceSet resourceSet;

    public DBTransaction() {
    }

    public DBTransaction(boolean readOnly, DBServer dbServer) {
        this.readOnly = readOnly;
        this.dbServer = dbServer;
    }

    protected abstract DBResource get(String id);

    protected abstract Stream<DBResource> findByPath(String path);

    protected abstract Stream<DBResource> findAll();

    protected abstract Stream<DBResource> findByClass(String classUri);

    protected abstract Stream<DBResource> findByClassAndQName(String classUri, String qName);

    protected abstract Stream<DBResource> findReferencedTo(String id);

    protected abstract void insert(DBResource dbResource);

    protected abstract void update(DBResource oldDbResource, DBResource dbResource);

    protected abstract void delete(DBResource dbResource);

    public abstract boolean truncate();

    public void begin() {
    }

    public void commit() {
    }

    public void rollback() {
    }

    protected DBResource getOrThrow(String id) {
        DBResource dbResource = get(id);
        if (dbResource == null) {
            throw new IllegalArgumentException("Object not found: " + id);
        }
        return dbResource;
    }

    protected void loadImage(DBResource dbResource, Resource resource) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dbResource.getImage());
        try {
            resource.load(inputStream, null);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
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

    protected DBResource fillDbResource(Resource resource, DBResource dbResource) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.save(outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dbResource.setImage(outputStream.toByteArray());
        fillIndexes(resource, dbResource);
        return dbResource;
    }

    protected void fillIndexes(Resource resource, DBResource dbResource) {
        List<DBObject> dbObjects = resource.getContents().stream().map(eObject -> {
                    DBObject dbObject = new DBObject();
                    URI classUri = EcoreUtil.getURI(eObject.eClass()).trimQuery();
                    dbObject.setClassUri(classUri.toString());
                    dbObject.setQName(getDbServer().getQName(eObject));
                    return dbObject;
                }
        ).collect(Collectors.toList());
        dbResource.setDbObjects(dbObjects);
        Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(resource);
        Set<String> references = xrs.keySet().stream()
                .map(eObject -> {
                    URI uri = EcoreUtil.getURI(eObject);
                    if (uri.isRelative()) {
                        uri = uri.resolve(resource.getURI());
                    }
                    else if (!getDbServer().canHandle(uri)) {
                        return null;
                    }
                    return getDbServer().getId(uri);
                })
                .filter(s -> s != null && !s.equals(dbResource.getId()))
                .collect(Collectors.toSet());
        dbResource.setReferences(references);
    }

    public Stream<Resource> findAll(ResourceSet rs) {
        return findAll()
                .map(dbResource -> createResource(rs, dbResource));
    }

    public Stream<Resource> findByPath(ResourceSet rs, String path) {
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return findByPath(path)
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
        String id = dbServer.getId(resource.getURI());
        String version = dbServer.getVersion(resource.getURI());
        ResourceSet rs = getResourceSet();
        Resource oldResource = rs.createResource(resource.getURI());
        DBResource oldDbResource = null;
        if (id != null) {
            oldDbResource = get(id);
            if (oldDbResource != null) {
                if (version == null) {
                    throw new IllegalArgumentException(String.format("Version for updated resource %s not defined", id));
                }
                String oldVersion = oldDbResource.getVersion();
                if (!version.equals(oldVersion)) {
                    throw new IllegalArgumentException(String.format(
                            "Version (%s) for updated resource %s is not equals to the version in the DB (%s)",
                            version, id, oldVersion));
                }
                load(oldDbResource, oldResource);
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
                .map(sameName -> getDbServer().getId(sameName.getURI()))
                .collect(Collectors.toList());
        if (sameResources.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate object names in resources (%s)",
                    String.join(", ", sameResources)));
        }
        dbServer.getEvents().fireBeforeSave(oldResource, resource);
        DBResource newDbResource;
        if (oldDbResource == null) {
            if (id == null) {
                id = getNextId();
            }
            resource.setURI(getDbServer().createURI(id));
            newDbResource = createDBResource(resource, id, version);
            insert(newDbResource);
        } else {
            newDbResource = createDBResource(resource, oldDbResource.getId(), oldDbResource.getVersion());
            update(oldDbResource, newDbResource);
        }
        dbServer.getEvents().fireAfterSave(oldResource, resource);
        resource.setURI(getDbServer().createURI(newDbResource.getId(), newDbResource.getVersion()));
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

    protected String getNextId() {
        return EcoreUtil.generateUUID();
    }

    public void load(Resource resource) {
        resource.unload();
        String id = dbServer.getId(resource.getURI());
        DBResource dbResource = getOrThrow(id);
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
        DBResource dbResource = getOrThrow(id);
        String oldVersion = dbResource.getVersion();
        if (!version.equals(oldVersion)) {
            throw new IllegalArgumentException(String.format(
                    "Version (%s) for deleted object %s is not equals to the version in the DB (%s)",
                    version, id, oldVersion));
        }
        ResourceSet rs = getResourceSet();
        Resource oldResource = rs.createResource(uri);
        load(dbResource, oldResource);
        List<String> refs = findReferencedTo(oldResource)
                .map(resource -> getDbServer().getId(resource.getURI()))
                .collect(Collectors.toList());
        if (refs.size() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Can't delete referenced resource (%s)",
                    String.join(", ", refs)));
        }
        dbServer.getEvents().fireBeforeDelete(oldResource);
        delete(dbResource);
    }

    public ResourceSet getResourceSet() {
        if (resourceSet == null) {
            resourceSet = createResourceSet();
        }
        return resourceSet;
    }

    private ResourceSet createResourceSet() {
        ResourceSetImpl result = new ResourceSetImpl();
        EPackage.Registry registry = new DBPackageRegistry(getDbServer().getPackageRegistry(), this);
        result.setPackageRegistry(registry);
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
