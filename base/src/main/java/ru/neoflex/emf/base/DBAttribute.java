package ru.neoflex.emf.base;

import javax.persistence.*;

@Entity
public class DBAttribute {
    @EmbeddedId
    private DBAttributeId id = new DBAttributeId();

    @ManyToOne
    @MapsId("dbObjectId")
    private DBObject dbObject;

    @Column(length = 10485760)
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public DBAttributeId getId() {
        return id;
    }

    public void setId(DBAttributeId id) {
        this.id = id;
    }

    public DBObject getDbObject() {
        return dbObject;
    }

    public void setDbObject(DBObject dbObject) {
        this.dbObject = dbObject;
    }
}
