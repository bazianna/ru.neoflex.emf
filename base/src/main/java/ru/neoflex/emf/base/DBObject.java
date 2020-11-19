package ru.neoflex.emf.base;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class DBObject {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column()
    private Integer version;
    @Column(name = "class_uri", length = 512)
    private String classUri;
    @Column(name = "q_name", length = 512)
    private String qName;
    @Column(name = "proxy", length = 512)
    private String proxy;
    @Column(length = 10485760)
    private byte[] image;
    @ElementCollection
    @CollectionTable(indexes = {
            @Index(columnList = "db_object_id", name = "DBObject_DBReference_ie2")
    })
    private List<DBReference> references;

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

    public String getqName() {
        return qName;
    }

    public void setqName(String qName) {
        this.qName = qName;
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
}
