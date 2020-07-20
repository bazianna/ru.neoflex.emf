package ru.neoflex.emf.base;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Set;

@Entity
public class DBResource implements Serializable, Cloneable {
    @Id
    @Column(length = 32)
    private String id;
    @Column(length = 32)
    private String version;
    @ElementCollection
    @Column(name = "names", length = 1024)
    @CollectionTable(indexes = {
            @Index(columnList = "names")
    })
    private Set<String> names;
    @ElementCollection
    @Column(name = "references", length = 1024)
    @CollectionTable(indexes = {
            @Index(columnList = "references")
    })
    private Set<String> references;
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

    public Set<String> getNames() {
        return names;
    }

    public void setNames(Set<String> names) {
        this.names = names;
    }
}
