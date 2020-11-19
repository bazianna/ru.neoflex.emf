package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class DBResource extends ResourceImpl {
    protected Map<Long, EObject> idToEObjectMap;
    protected Map<EObject, Long> eObjectToIDMap;
    protected Map<EObject, Integer> eObjectToVersionMap;

    public DBResource() {
        super();
    }

    public DBResource(URI uri) {
        super(uri);
    }

    protected Map<EObject, Integer> getEObjectToVersionMap() {
        if (eObjectToVersionMap == null) {
            eObjectToVersionMap = new HashMap<>();
        }

        return eObjectToVersionMap;
    }

    protected Map<Long, EObject> getIDToEObjectMap() {
        if (idToEObjectMap == null) {
            idToEObjectMap = new HashMap<>();
        }

        return idToEObjectMap;
    }

    protected Map<EObject, Long> getEObjectToIDMap() {
        if (eObjectToIDMap == null) {
            eObjectToIDMap = new HashMap<>();
        }

        return eObjectToIDMap;
    }

    public void setID(EObject eObject, Long id) {
        Object oldID = id != null ? getEObjectToIDMap().put(eObject, id) : getEObjectToIDMap().remove(eObject);

        if (oldID != null) {
            getIDToEObjectMap().remove(oldID);
        }

        if (id != null) {
            getIDToEObjectMap().put(id, eObject);
        }
    }

    public void setVersion(EObject eObject, Integer version) {
        if (version != null) {
            getEObjectToVersionMap().put(eObject, version);
        } else {
            getEObjectToVersionMap().remove(eObject);
        }
    }

    public Long getID(EObject eObject) {
        return getEObjectToIDMap().get(eObject);
    }

    public Integer getVersion(EObject eObject) {
        return eObjectToVersionMap.get(eObject);
    }

    @Override
    public String getURIFragment(EObject eObject) {
        Long idl = getID(eObject);
        return idl != null ? idl.toString() : super.getURIFragment(eObject);
    }

    @Override
    protected EObject getEObjectByID(String id) {
        Long idl;
        try {
            idl = Long.parseLong(id);
        } catch (NumberFormatException e) {
            idl = null;
        }
        EObject eObject = idl != null ? getIDToEObjectMap().get(idl) : null;
        return eObject != null ? eObject : super.getEObjectByID(id);
    }

    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException
    {
        DBOutputStream dbOutputStream = (DBOutputStream) outputStream;
        dbOutputStream.saveResource(this);
    }

    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException
    {
        DBInputStream dbInputStream = (DBInputStream) inputStream;
        dbInputStream.loadResource(this);
    }
}
