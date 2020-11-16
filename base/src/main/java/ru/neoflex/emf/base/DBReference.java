package ru.neoflex.emf.base;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

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
    @ManyToOne()
    @JoinColumn(name = "db_object_id", nullable = false)
    private
    DBObject dbObject;

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

    public DBObject getDbObject() {
        return dbObject;
    }

    public void setDbObject(DBObject dbObject) {
        this.dbObject = dbObject;
    }
}
