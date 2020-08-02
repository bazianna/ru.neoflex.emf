package ru.neoflex.emf.base;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class DBObject implements Serializable, Cloneable {
    @Column(name = "class_uri", length = 512)
    private String classUri;
    @Column(name = "q_name", length = 512)
    private String qName;

    public DBObject() {
    }

    public DBObject(String classUri, String qName) {
        this.classUri = classUri;
        this.qName = qName;
    }

    public DBObject clone() {
        try {
            return (DBObject) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getClassUri() {
        return classUri;
    }

    public void setClassUri(String classUri) {
        this.classUri = classUri;
    }

    public String getQName() {
        return qName;
    }

    public void setQName(String qName) {
        this.qName = qName;
    }
}
