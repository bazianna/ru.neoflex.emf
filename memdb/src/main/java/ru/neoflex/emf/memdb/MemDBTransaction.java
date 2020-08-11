package ru.neoflex.emf.memdb;

import org.prevayler.Transaction;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.util.*;
import java.util.stream.Stream;

public class MemDBTransaction extends DBTransaction implements Transaction<MemDBModel> {
    private transient MemDBModel memDBModel;
    private String tenantId;
    private Map<String, DBResource> inserted = new HashMap<>();
    private Map<String, DBResource> updated = new HashMap<>();
    private Set<String> deleted = new HashSet<>();

    public MemDBTransaction() {
    }

    public MemDBTransaction(boolean readOnly, DBServer dbServer) {
        super(readOnly, dbServer);
    }

    @Override
    protected DBResource get(String id) {
        if (deleted.contains(id)) {
            return null;
        }
        DBResource dbObject = updated.get(id);
        if (dbObject == null) {
            dbObject = inserted.get(id);
        }
        if (dbObject == null) {
            dbObject = memDBModel.get(tenantId, id);
        }
        if (dbObject == null) {
            return null;
        }
        return dbObject.clone();
    }

    @Override
    public Stream<DBResource> findAll() {
        Stream<DBResource> baseStream = memDBModel.findAll(tenantId)
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
        Stream<DBResource> baseStream = memDBModel.findByClass(tenantId, classUri)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getDbObjects().stream().anyMatch(s -> Objects.equals(classUri, s.getClassUri())));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getDbObjects().stream().anyMatch(s -> Objects.equals(classUri, s.getClassUri())));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        Stream<DBResource> baseStream = memDBModel.findByClassAndQName(tenantId, classUri, qName)
                .filter(dbResource -> !deleted.contains(dbResource.getId()) && !updated.containsKey(dbResource.getId()));
        Stream<DBResource> insertedStream = inserted.values().stream()
                .filter(dbResource -> dbResource.getDbObjects().stream()
                        .anyMatch(s->Objects.equals(classUri, s.getClassUri()) && Objects.equals(qName, s.getQName())));
        Stream<DBResource> updatedStream = updated.values().stream()
                .filter(dbResource -> dbResource.getDbObjects().stream()
                        .anyMatch(s->Objects.equals(classUri, s.getClassUri()) && Objects.equals(qName, s.getQName())));
        return Stream.concat(
                Stream.concat(insertedStream, updatedStream),
                baseStream
        );
    }

    @Override
    public Stream<DBResource> findReferencedTo(String id) {
        Stream<DBResource> baseStream = memDBModel.findReferencedTo(tenantId, id)
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
            prevalentSystem.delete(tenantId, id);
        });
        updated.values().forEach(dbResource->{
            prevalentSystem.update(tenantId, dbResource);
        });
        inserted.values().forEach(dbResource->{
            prevalentSystem.insert(tenantId, dbResource);
        });
        reset();
    }

    public MemDBServer getMemDbServer() {
        return (MemDBServer) getDbServer();
    }

    @Override
    protected void insert(DBResource dbResource) {
        dbResource.setVersion("0");
        inserted.put(dbResource.getId(), dbResource);
    }

    @Override
    protected void update(DBResource dbResource) {
        dbResource.setVersion(String.valueOf(1 + Integer.parseInt(dbResource.getVersion())));
        updated.put(dbResource.getId(), dbResource);
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
