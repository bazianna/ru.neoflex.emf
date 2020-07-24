package ru.neoflex.emf.gitdb;

import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.util.stream.Stream;

public class GitDBTransaction extends DBTransaction {

    public GitDBTransaction(boolean readOnly, DBServer dbServer) {
        super(readOnly, dbServer);
    }

    @Override
    protected DBResource get(String id) {
        return null;
    }

    @Override
    protected Stream<DBResource> findAll() {
        return null;
    }

    @Override
    protected Stream<DBResource> findByClass(String classUri) {
        return null;
    }

    @Override
    protected Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        return null;
    }

    @Override
    protected Stream<DBResource> findReferencedTo(String id) {
        return null;
    }

    @Override
    protected void insert(DBResource dbResource) {

    }

    @Override
    protected void update(String id, DBResource dbResource) {

    }

    @Override
    protected void delete(String id) {

    }
}
