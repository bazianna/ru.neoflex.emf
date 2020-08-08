package ru.neoflex.emf.memdb;

import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.Query;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.util.Properties;

public class MemDBServer extends DBServer {
    public static final String MEMDB = "memdb";
    public static final String CONFIG_DEFAULT_TENANT = "emfdb.mem.defaultTenant";
    public static final String CONFIG_PREVALENCE_BASE = "emfdb.mem.prevalenceBase";
    protected final Prevayler<MemDBModel> prevayler;

    public MemDBServer(String dbName, Properties config) throws Exception {
        super(dbName, config);
        setTenantId(getConfig().getProperty(CONFIG_DEFAULT_TENANT, "default"));
        String prevalenceBase = config.getProperty(CONFIG_PREVALENCE_BASE, System.getProperty("user.home") + "/.memdb/" + dbName);
        prevayler = PrevaylerFactory.createPrevayler(new MemDBModel(), prevalenceBase);
    }

    @Override
    public void close() throws IOException {
        try {
            prevayler.takeSnapshot();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        prevayler.close();
    }

    public Prevayler<MemDBModel> getPrevayler() {
        return prevayler;
    }

    @Override
    protected DBTransaction createDBTransaction(boolean readOnly) {
        DBTransaction tx = new MemDBTransaction(readOnly, this);
        return tx;
    }

    @Override
    public String getScheme() {
        return MEMDB;
    }

    @Override
    protected  <R> R callWithTransaction(DBTransaction tx, TxFunction<R> f) throws Exception {
        return prevayler.execute((Query<MemDBModel, R>) (prevalentSystem, executionTime) -> {
            ((MemDBTransaction)tx).setMemDBModel(prevalentSystem);
            return f.call(tx);
        });
    }
}
