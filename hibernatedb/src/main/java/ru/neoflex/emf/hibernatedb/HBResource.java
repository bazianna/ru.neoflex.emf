package ru.neoflex.emf.hibernatedb;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.Set;

@Entity
public class HBResource implements Serializable, Cloneable {
    @Id
    private String id;
    private int version;
    @ElementCollection
    private Set<String> names;
    @ElementCollection
    private Set<String> references;
    private byte[] image;

    HBResource() {
        this.id = null;
        this.version = 0;
    }

    public String getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public void setId(String id) {
        this.id = id;
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
