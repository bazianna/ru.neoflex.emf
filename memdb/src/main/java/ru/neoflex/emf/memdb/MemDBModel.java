package ru.neoflex.emf.memdb;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.index.hash.HashIndex;
import com.googlecode.cqengine.index.radix.RadixTreeIndex;
import com.googlecode.cqengine.index.unique.UniqueIndex;
import com.googlecode.cqengine.query.QueryFactory;
import ru.neoflex.emf.base.DBResource;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MemDBModel implements Externalizable {
    private Map<String, IndexedCollection<DBResource>> indexedCollection = new HashMap<>();
    public static final Attribute<DBResource, String> ID = QueryFactory.attribute("id", DBResource::getId);
    public static final Attribute<DBResource, String> NAMES = QueryFactory.attribute(String.class, "classUri", DBResource::getQNames);
    public static final Attribute<DBResource, String> REFERENCES = QueryFactory.attribute(String.class,"references", DBResource::getReferences);

    public DBResource get(String tenantId, String id) {
        return getIndexedCollection(tenantId).retrieve(QueryFactory.equal(ID, id)).uniqueResult();
    }

    public Stream<DBResource> findAll(String tenantId) {
        return getIndexedCollection(tenantId).retrieve(QueryFactory.all(DBResource.class)).stream();
    }

    public Stream<DBResource> findByClass(String tenantId, String classUri) {
        String attributeValue = classUri + ":";
        return getIndexedCollection(tenantId).retrieve(QueryFactory.startsWith(NAMES, attributeValue)).stream();
    }

    public Stream<DBResource> findByClassAndQName(String tenantId, String classUri, String qName) {
        String attributeValue = classUri + ":" + qName;
        return getIndexedCollection(tenantId).retrieve(QueryFactory.equal(NAMES, attributeValue)).stream();
    }

    public Stream<DBResource> findReferencedTo(String tenantId, String id) {
        return getIndexedCollection(tenantId).retrieve(QueryFactory.equal(REFERENCES, id)).stream();
    }

    public void insert(String tenantId, DBResource dbResource) {
        getIndexedCollection(tenantId).add(dbResource);
    }

    public void update(String tenantId, DBResource dbResource) {
        delete(tenantId, dbResource.getId());
        insert(tenantId, dbResource);
    }

    public void delete(String tenantId, String id) {
        DBResource dbResource = get(tenantId, id);
        getIndexedCollection(tenantId).remove(dbResource);
    }

    public IndexedCollection<DBResource> getIndexedCollection(String tenantId) {
        return indexedCollection.computeIfAbsent(tenantId, newTenantId -> {
            IndexedCollection newCollection = new ConcurrentIndexedCollection<>();
            newCollection.addIndex(UniqueIndex.onAttribute(ID));
            newCollection.addIndex(RadixTreeIndex.onAttribute(NAMES));
            newCollection.addIndex(HashIndex.onAttribute(REFERENCES));
            return newCollection;
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        Map<String, List<DBResource>> dbResources = new HashMap<>();
        indexedCollection.forEach((tenantId, collection) -> {
            List<DBResource> list = collection.retrieve(QueryFactory.all(DBResource.class)).stream().collect(Collectors.toList());
            dbResources.put(tenantId, list);
        });
        out.writeObject(dbResources);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        Map<String, List<DBResource>> dbResources = (Map<String, List<DBResource>>) in.readObject();
        dbResources.forEach((tenantId, list) -> getIndexedCollection(tenantId).addAll(list));
    }
}
