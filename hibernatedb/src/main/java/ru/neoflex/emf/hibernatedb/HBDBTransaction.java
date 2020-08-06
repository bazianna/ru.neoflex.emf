package ru.neoflex.emf.hibernatedb;

import org.hibernate.Session;
import org.hibernate.Transaction;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.util.stream.Stream;

public class HBDBTransaction extends DBTransaction {
    private final Session session;
    private Transaction tx;

    public HBDBTransaction(boolean readOnly, DBServer dbServer) {
        super(readOnly, dbServer);
        session = getHbDbServer().getSessionFactory().openSession();
    }

    public void close() {
        session.close();
    }

    public HBDBServer getHbDbServer() {
        return (HBDBServer) getDbServer();
    }

    @Override
    protected DBResource get(String id) {
        return session.load(DBResource.class, id);
    }

    @Override
    protected Stream<DBResource> findAll() {
        return session.createQuery("select r from DBResource r", DBResource.class).getResultStream();
    }

    @Override
    protected Stream<DBResource> findByClass(String classUri) {
        return session.createQuery("select r from DBResource r join r.dbObjects name where name.classUri = :classUri", DBResource.class)
                .setParameter("classUri", classUri)
                .getResultStream();
    }

    @Override
    protected Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        return session.createQuery("select r from DBResource r join r.dbObjects name where name.classUri = :classUri and name.qName = :qName", DBResource.class)
                .setParameter("classUri", classUri)
                .setParameter("qName", qName)
                .getResultStream();
    }

    @Override
    protected Stream<DBResource> findReferencedTo(String id) {
        return session.createQuery("select r from DBResource r join r.references reference where reference = :id", DBResource.class)
                .setParameter("id", id)
                .getResultStream();
    }

    @Override
    protected void insert(DBResource dbResource) {
        dbResource.setId(getNextId());
        dbResource.setVersion("0");
        session.persist(dbResource);
    }

    @Override
    protected void update(DBResource dbResource) {
        dbResource.setVersion(String.valueOf(1 + Integer.parseInt(dbResource.getVersion())));
        session.update(dbResource);
    }

    @Override
    protected void delete(String id) {
        session.delete(get(id));
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
