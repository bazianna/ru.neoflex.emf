package ru.neoflex.emf.base;

import javax.persistence.*;

@Embeddable
public class DBReference {
    @Column(length = 512)
    private
    String feature;

    @Column
    private
    Integer index;

    @Column
    private
    Boolean containment;

    @Column(name = "dbobject_id", insertable=false, updatable = false)
    private Long dbObjectId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "refobject_id", nullable = false)
    private DBObject refObject;

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Boolean getContainment() {
        return containment;
    }

    public void setContainment(Boolean contained) {
        this.containment = contained;
    }

    public DBObject getRefObject() {
        return refObject;
    }

    public void setRefObject(DBObject dbObject) {
        this.refObject = dbObject;
    }

    public Long getDbObjectId() {
        return dbObjectId;
    }

    public void setDbObjectId(Long dbobject_id) {
        this.dbObjectId = dbobject_id;
    }
}
