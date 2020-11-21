package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class HbOutputStream extends OutputStream implements URIConverter.Saveable {
    private HbTransaction tx;
    private URI uri;
    private Map<?, ?> options;

    public HbOutputStream(HbTransaction tx, URI uri, Map<?, ?> options) {
        this.tx = tx;
        this.uri = uri;
        this.options = options;
    }

    @Override
    public void saveResource(Resource resource) throws IOException {
        tx.save((HbResource) resource);
    }

    @Override
    public void write(int b) throws IOException {
    }
}
