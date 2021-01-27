package ru.neoflex.emf.base;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DBReferenceId implements Serializable {
    private Long dbObjectId;
    @Column(length = 512)
    private String feature;
    @Column
    private Integer index;

    public Long getDbObjectId() {
        return dbObjectId;
    }

    public void setDbObjectId(Long dbObjectId) {
        this.dbObjectId = dbObjectId;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((dbObjectId == null) ? 0 : dbObjectId.hashCode());
        result = prime * result
                + ((feature == null) ? 0 : feature.hashCode());
        result = prime * result
                + ((index == null) ? 0 : index.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DBReferenceId other = (DBReferenceId) obj;
        return Objects.equals(getDbObjectId(), other.getDbObjectId()) &&
                Objects.equals(getFeature(), other.getFeature()) &&
                Objects.equals(getIndex(), other.getIndex());
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}
