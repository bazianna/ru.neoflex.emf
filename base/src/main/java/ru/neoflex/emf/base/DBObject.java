package ru.neoflex.emf.base;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(indexes = {
        @Index(columnList = "class_uri"),
        @Index(columnList = "container_id,index")
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
            @Index(columnList = "refobject_id"),
            @Index(columnList = "dbobject_id,index")
    }, joinColumns = @JoinColumn(name = "dbobject_id"))
    private List<DBReference> references;

    @ElementCollection
    @CollectionTable(indexes = {
            @Index(columnList = "feature,value")
    })
    private List<DBAttribute> attributes;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "container")
    private List<DBObject> content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id")
    private DBObject container;

    @Column(name = "index")
    private Integer index;
    @Column(name = "feature")
    private String feature;

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

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public List<DBObject> getContent() {
        if (content == null) {
            content = new ArrayList<>();
        }
        return content;
    }

    public void setContent(List<DBObject> content) {
        this.content = content;
    }

    public DBObject getContainer() {
        return container;
    }

    public void setContainer(DBObject container) {
        this.container = container;
    }
}
