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
        return session.get(DBResource.class, id);
    }

    @Override
    protected Stream<DBResource> findByPath(String path) {
        return session.createQuery("select r from DBResource r where r.id like concat(:id, '%')", DBResource.class)
                .setParameter("id", path)
                .getResultStream();
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
        dbResource.setVersion("0");
        session.persist(dbResource);
    }

    @Override
    protected void update(DBResource oldDbResource, DBResource dbResource) {
        oldDbResource.setVersion(String.valueOf(1 + Integer.parseInt(oldDbResource.getVersion())));
        oldDbResource.setImage(dbResource.getImage());
        oldDbResource.setDbObjects(dbResource.getDbObjects());
        oldDbResource.setReferences(dbResource.getReferences());
        session.update(oldDbResource);
    }

    @Override
    protected void delete(DBResource dbResource) {
        session.delete(dbResource);
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
