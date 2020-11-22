package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class HbResource extends ResourceImpl {
    protected Map<Long, EObject> idToEObjectMap;

    public HbResource(URI uri) {
        super(uri);
    }

    protected Map<Long, EObject> getIDToEObjectMap() {
        if (idToEObjectMap == null) {
            idToEObjectMap = new HashMap<>();
        }

        return idToEObjectMap;
    }

    @Override
    public String getURIFragment(EObject eObject) {
        Long idl = getTx().getDbServer().getId(eObject);
        return idl != null ? idl.toString() : super.getURIFragment(eObject);
    }

    @Override
    protected EObject getEObjectByID(String id) {
        try {
            Long idl = Long.parseLong(id);
            EObject eObject = getIDToEObjectMap().get(idl);
            return eObject != null ? eObject : super.getEObjectByID(id);
        } catch (NumberFormatException e) {
            return super.getEObjectByID(id);
        }
    }

    protected boolean isAttachedDetachedHelperRequired() {
        return true;
    }

    public HbTransaction getTx() {
        HbHandler hbHandler = (HbHandler) getResourceSet().getURIConverter().getURIHandlers().get(0);
        return hbHandler.getTx();
    }

    protected void attachedHelper(EObject eObject) {
        Long id = getTx().getDbServer().getId(eObject);
        if (id != null) {
            getIDToEObjectMap().put(id, eObject);
        }
    }

    protected void detachedHelper(EObject eObject) {
        Long id = getTx().getDbServer().getId(eObject);
        if (id != null) {
            getIDToEObjectMap().remove(id);
        }
    }

    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        HbOutputStream hbOutputStream = (HbOutputStream) outputStream;
        hbOutputStream.saveResource(this);
    }

    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        HbInputStream hbInputStream = (HbInputStream) inputStream;
        hbInputStream.loadResource(this);
    }
}
