package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class HbHandler extends URIHandlerImpl {
    private HbTransaction tx;

    HbHandler(HbTransaction tx) {
        this.tx = tx;
    }

    @Override
    public boolean canHandle(URI uri) {
        return tx.getDbServer().canHandle(uri);
    }

    @Override
    public OutputStream createOutputStream(URI uri, Map<?, ?> options) throws IOException {
        return new HbOutputStream(tx, uri, options);
    }

    @Override
    public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
        return new HbInputStream(tx, uri, options);
    }

    @Override
    public void delete(URI uri, Map<?, ?> options) throws IOException {
        tx.delete(uri);
    }

    public HbTransaction getTx() {
        return tx;
    }
}
