package ru.neoflex.emf.memdb;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.prevayler.Transaction;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.util.*;
import java.util.stream.Stream;

public class MemDBTransaction extends DBTransaction implements Transaction<MemDBModel> {
    private transient MemDBModel memDBModel;
    private Map<String, DBResource> inserted = new HashMap<>();
    private Map<String, DBResource> updated = new HashMap<>();
    private Set<String> deleted = new HashSet<>();

    public MemDBTransaction() {
    }

    public MemDBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        super(readOnly, dbServer, tenantId);
    }

    @Override
    protected DBResource get(String id) {
        if (deleted.contains(id)) {
            throw new IllegalArgumentException(String.format("Can't find object %s", id));
        }
        DBResource dbObject = updated.get(id);
        if (dbObject == null) {
            dbObject = inserted.get(id);
        }
        if (dbObject == null) {
            dbObject = memDBModel.get(getTenantId(), id);
        }
        return dbObject.clone();
    }

    @Override
    public Stream<DBResource> findAll() {
        Stream<DBResource> baseStream = memDBModel.findAll(getTenantId())
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream();
        Stream<DBResource> updatedStream = updated.values().stream();
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public Stream<DBResource> findByClass(String classUri) {
        String attributeValue = classUri + ":";
        Stream<DBResource> baseStream = memDBModel.findByClass(getTenantId(), classUri)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getNames().stream().anyMatch(s -> s.startsWith(attributeValue)));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getNames().stream().anyMatch(s -> s.startsWith(attributeValue)));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        String attributeValue = classUri + ":" + qName;
        Stream<DBResource> baseStream = memDBModel.findByClassAndQName(getTenantId(), classUri, qName)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getNames().contains(attributeValue));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getNames().contains(attributeValue));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public Stream<DBResource> findReferencedTo(String id) {
        Stream<DBResource> baseStream = memDBModel.findReferencedTo(getTenantId(), id)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getReferences().contains(id));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getReferences().contains(id));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public void executeOn(MemDBModel prevalentSystem, Date executionTime) {
        deleted.stream().forEach(id -> {
            prevalentSystem.delete(getTenantId(), id);
        });
        updated.values().forEach(dbResource->{
            prevalentSystem.update(getTenantId(), dbResource);
        });
        inserted.values().forEach(dbResource->{
            prevalentSystem.insert(getTenantId(), dbResource);
        });
        reset();
    }

    public MemDBServer getMemDbServer() {
        return (MemDBServer) getDbServer();
    }

    @Override
    protected void insert(DBResource dbResource) {
        dbResource.setId(getNextId());
        dbResource.setVersion("0");
        inserted.put(dbResource.getId(), dbResource);
    }

    @Override
    protected void update(String id, DBResource dbResource) {
        dbResource.setVersion(String.valueOf(1 + Integer.parseInt(dbResource.getVersion())));
        updated.put(id, dbResource);
    }

    @Override
    protected void delete(String id) {
        deleted.add(id);
    }

    @Override
    public void commit() {
        if (!isReadOnly()) {
            getMemDbServer().getPrevayler().execute(this);
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
