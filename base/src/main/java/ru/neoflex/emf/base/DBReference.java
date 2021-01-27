package ru.neoflex.emf.base;

import javax.persistence.*;

@Entity
public class DBReference {
    @EmbeddedId
    private DBReferenceId id = new DBReferenceId();

    @ManyToOne
    @MapsId("dbObjectId")
    private DBObject dbObject;

    @ManyToOne
    private DBObject refObject;

    public DBObject getRefObject() {
        return refObject;
    }

    public void setRefObject(DBObject dbObject) {
        this.refObject = dbObject;
    }

    public DBReferenceId getId() {
        return id;
    }

    public void setId(DBReferenceId id) {
        this.id = id;
    }

    public DBObject getDbObject() {
        return dbObject;
    }

    public void setDbObject(DBObject dbObject) {
        this.dbObject = dbObject;
    }
}
