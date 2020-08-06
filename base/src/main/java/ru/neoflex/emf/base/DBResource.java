package ru.neoflex.emf.base;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class DBResource implements Serializable, Cloneable {
    @Id
    @Column(length = 32)
    private String id;
    @Column(length = 32)
    private String version;
    @ElementCollection
    @CollectionTable(indexes = {
            @Index(columnList = "class_uri,q_name", unique = true, name = "DBResource_DBObjects_ak")
    })
    private List<DBObject> dbObjects = new ArrayList<>();
    @ElementCollection
    @Column(length = 1024)
    @CollectionTable(indexes = {
            @Index(columnList = "references", name = "DBResource_References_ie1")
    })
    private Set<String> references = new HashSet<>();
    @Column(length = 10485760)
    private byte[] image;

    public DBResource() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public Set<String> getReferences() {
        return references;
    }

    public void setReferences(Set<String> references) {
        this.references = references;
    }

    public List<DBObject> getDbObjects() {
        return dbObjects;
    }

    public Set<String> getQNames() {
        return dbObjects.stream().map(dbObject -> dbObject.getClassUri() +":" + dbObject.getQName()).collect(Collectors.toSet());
    }

    public void setDbObjects(List<DBObject> dbObjects) {
        this.dbObjects = dbObjects;
    }

    public DBResource clone() {
        try {
            return (DBResource) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
