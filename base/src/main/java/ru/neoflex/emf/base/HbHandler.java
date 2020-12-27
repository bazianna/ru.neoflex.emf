package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class HbHandler extends URIHandlerImpl {
    public static final String OPTION_GET_ROOT_CONTAINER = "__OPTION_GET_ROOT_CONTAINER__";
    private HbServer hbServer;
    private HbTransaction tx;

    HbHandler(HbServer hbServer, HbTransaction tx) {
        this.hbServer = hbServer;
        this.tx = tx;
    }

    @Override
    public boolean canHandle(URI uri) {
        return getDbServer().canHandle(uri);
    }

    @Override
    public OutputStream createOutputStream(URI uri, Map<?, ?> options) throws IOException {
        if (tx != null) {
            return new HbOutputStream(tx, uri, options);
        }
        HbTransaction effectiveTx = getDbServer().createDBTransaction(false);
        effectiveTx.begin();
        return new HbOutputStream(effectiveTx, uri, options) {
            @Override
            public void close() throws IOException {
                try {
                    this.tx.commit();
                }
                catch (Throwable e) {
                    this.tx.rollback();
                    throw e;
                }
                finally {
                    this.tx.close();
                }
            }
        };
    }

    @Override
    public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
        if (tx != null) {
            return new HbInputStream(tx, uri, options);
        }
        HbTransaction effectiveTx = getDbServer().createDBTransaction(true);
        return new HbInputStream(effectiveTx, uri, options) {
            @Override
            public void close() throws IOException {
                this.tx.close();
            }
        };
    }

    @Override
    public void delete(URI uri, Map<?, ?> options) throws IOException {
        if (tx != null) {
            tx.delete(uri);
        }
        else {
            HbTransaction effectiveTx = getDbServer().createDBTransaction(false);
            effectiveTx.begin();
            try {
                effectiveTx.delete(uri);
                effectiveTx.commit();
            }
            catch (Throwable e) {
                effectiveTx.rollback();
                throw e;
            }
            finally {
                effectiveTx.close();
            }
        }
    }

    public HbServer getDbServer() {
        return hbServer;
    }
}
