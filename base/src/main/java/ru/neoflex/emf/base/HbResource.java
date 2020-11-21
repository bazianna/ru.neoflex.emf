package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HbResource extends ResourceImpl {

    public HbResource(URI uri) {
        super(uri);
    }

    @Override
    public String getURIFragment(EObject eObject) {
        Long idl = getTx().getDbServer().getId(eObject);
        return idl != null ? idl.toString() : super.getURIFragment(eObject);
    }

    @Override
    protected EObject getEObjectByID(String id) {
        try {
            return getTx().getDbServer().getEObjectToIdMap().entrySet().stream()
                    .filter(entry -> entry.getValue().id.equals(Long.parseLong(id))).map(entry -> entry.getKey())
                    .findFirst().orElse(null);
        } catch (NumberFormatException e) {
            return super.getEObjectByID(id);
        }
    }

    public HbTransaction getTx() {
        HbHandler hbHandler = (HbHandler) getResourceSet().getURIConverter().getURIHandlers().get(0);
        return hbHandler.getTx();
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
