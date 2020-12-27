package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class HbInputStream extends InputStream implements URIConverter.Loadable {
    protected HbTransaction tx;
    private URI uri;
    private Map<String, Object> options;

    public HbInputStream(HbTransaction tx, URI uri, Map<?, ?> options) {
        this.tx = tx;
        this.uri = uri;
        this.options = (Map<String, Object>) options;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public void loadResource(Resource resource) throws IOException {
        tx.load((HbResource) resource, options);
    }
}
