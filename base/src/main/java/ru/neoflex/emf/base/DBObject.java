package ru.neoflex.emf.base;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(indexes = {
        @Index(columnList = "class_uri")
})
public class DBObject {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;
    @Column(name = "version")
    private Integer version;
    @Column(name = "class_uri", length = 512)
    private String classUri;
    @Column(name = "proxy", length = 512)
    private String proxy;
    @Column(length = 10485760)
    private byte[] image;
    @ElementCollection
    @CollectionTable(indexes = {
            @Index(columnList = "refobject_id")
    })
    private List<DBReference> references;
    @ElementCollection
    @CollectionTable(indexes = {
            @Index(columnList = "feature,value")
    })
    private List<DBAttribute> attributes;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getClassUri() {
        return classUri;
    }

    public void setClassUri(String classUri) {
        this.classUri = classUri;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public List<DBReference> getReferences() {
        if (references == null) {
            references = new ArrayList<>();
        }
        return references;
    }

    public void setReferences(List<DBReference> references) {
        this.references = references;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public boolean isProxy() {
        return this.proxy != null;
    }

    public List<DBAttribute> getAttributes() {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }
        return attributes;
    }

    public void setAttributes(List<DBAttribute> attributes) {
        this.attributes = attributes;
    }
}
