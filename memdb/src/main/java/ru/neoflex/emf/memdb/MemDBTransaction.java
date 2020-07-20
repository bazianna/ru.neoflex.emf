package ru.neoflex.emf.memdb;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.prevayler.Transaction;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBTransaction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MemDBTransaction extends DBTransaction implements Transaction<MemDBModel> {
    private transient MemDBModel memDBModel;
    private Map<String, DBResource> inserted = new HashMap<>();
    private Map<String, DBResource> updated = new HashMap<>();
    private Set<String> deleted = new HashSet<>();

    @Override
    protected void load(String id, Resource resource) {
        DBResource dbResource = get(id);
        load(dbResource, resource);
        URI uri = getMemDBServer().createURI(id, String.valueOf(dbResource.getVersion()));
        resource.setURI(uri);
    }

    private void load(DBResource dbResource, Resource resource) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dbResource.getImage());
        try {
            resource.load(inputStream, null);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Can't load %s", dbResource.getId()));
        }
    }

    private Resource createResource(ResourceSet rs, DBResource dbResource) {
        URI uri = getMemDBServer().createURI(dbResource.getId(), String.valueOf(dbResource.getVersion()));
        Resource resource = rs.createResource(uri);
        load(dbResource, resource);
        return resource;
    }

    private DBResource get(String id) {
        if (deleted.contains(id)) {
            throw new IllegalArgumentException(String.format("Can't find object %s", id));
        }
        DBResource dbObject = updated.get(id);
        if (dbObject == null) {
            dbObject = inserted.get(id);
        }
        if (dbObject == null) {
            dbObject = memDBModel.get(id);
        }
        return dbObject;
    }

    @Override
    public Stream<Resource> findAll(ResourceSet rs) {
        Stream<DBResource> baseStream = memDBModel.findAll()
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream();
        Stream<DBResource> updatedStream = updated.values().stream();
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        ).map(dbResource -> createResource(rs, dbResource));
    }

    @Override
    public Stream<Resource> findByClass(ResourceSet rs, String classUri) {
        String attributeValue = classUri + ":";
        Stream<DBResource> baseStream = memDBModel.findByClass(classUri)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getNames().stream().anyMatch(s -> s.startsWith(attributeValue)));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getNames().stream().anyMatch(s -> s.startsWith(attributeValue)));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        ).map(dbResource -> createResource(rs, dbResource));
    }

    @Override
    public Stream<Resource> findByClassAndQName(ResourceSet rs, String classUri, String qName) {
        String attributeValue = classUri + ":" + qName;
        Stream<DBResource> baseStream = memDBModel.findByClassAndQName(classUri, qName)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getNames().contains(attributeValue));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getNames().contains(attributeValue));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        ).map(dbResource -> createResource(rs, dbResource));
    }

    @Override
    public Stream<Resource> findReferencedTo(Resource resource) {
        ResourceSet rs = resource.getResourceSet();
        String id = getMemDBServer().getId(resource.getURI());
        Stream<DBResource> baseStream = memDBModel.findReferencedTo(id)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getReferences().contains(id));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getReferences().contains(id));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        ).map(dbResource -> createResource(rs, dbResource));
    }

    @Override
    public void executeOn(MemDBModel prevalentSystem, Date executionTime) {
        deleted.stream().forEach(id -> {
            prevalentSystem.delete(id);
        });
        updated.values().forEach(dbResource->{
            prevalentSystem.update(dbResource);
        });
        inserted.values().forEach(dbResource->{
            prevalentSystem.insert(dbResource);
        });
        reset();
    }

    public MemDBServer getMemDBServer() {
        return (MemDBServer) getDbServer();
    }

    private DBResource createDBResource(Resource resource, String id, String version) {
        DBResource dbResource = new DBResource();
        dbResource.setId(id);
        dbResource.setVersion(version);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            resource.save(outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dbResource.setImage(outputStream.toByteArray());
        Set<String> names = resource.getContents().stream().map(eObject ->
            EcoreUtil.getURI(eObject.eClass()).toString() + ":" + getMemDBServer().getQName(eObject)
        ).collect(Collectors.toSet());
        dbResource.setNames(names);
        Map<EObject, Collection<EStructuralFeature.Setting>> xrs = EcoreUtil.ExternalCrossReferencer.find(resource);
        Set<String> references = xrs.keySet().stream()
                .map(eObject -> getMemDBServer().getId(EcoreUtil.getURI(eObject))).collect(Collectors.toSet());
        dbResource.setReferences(references);
        return dbResource;
    }

    @Override
    protected void insert(Resource resource) {
        DBResource dbResource = createDBResource(resource, getNextId(), "0");
        resource.setURI(getMemDBServer().createURI(dbResource.getId(), String.valueOf(dbResource.getVersion())));
        inserted.put(dbResource.getId(), dbResource);
    }

    @Override
    protected void update(String id, Resource resource) {
        Integer version = Integer.valueOf(getMemDBServer().getVersion(resource.getURI()));
        DBResource dbResource = createDBResource(resource, id, String.valueOf(version + 1));
        resource.setURI(getMemDBServer().createURI(dbResource.getId(), dbResource.getVersion()));
        updated.put(id, dbResource);
    }

    @Override
    protected void delete(String id) {
        deleted.add(id);
    }

    @Override
    public void commit() {
        if (!isReadOnly()) {
            getMemDBServer().getPrevayler().execute(this);
        }
    }

    public void reset() {
        inserted.clear();
        updated.clear();
        deleted.clear();
    }

    public MemDBModel getMemDBModel() {
        return memDBModel;
    }

    public void setMemDBModel(MemDBModel memDBModel) {
        this.memDBModel = memDBModel;
    }
}
