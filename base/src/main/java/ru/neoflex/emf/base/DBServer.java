package ru.neoflex.emf.base;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class DBServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DBServer.class);
    public static final String CONFIG_DBTYPE = "emfdb.dbtype";
    protected static final ThreadLocal<String> tenantId = new InheritableThreadLocal<>();
    private final String dbName;
    private final Events events = new Events();
    private Function<EClass, EStructuralFeature> qualifiedNameDelegate = eClass -> eClass.getEStructuralFeature("name");
    private final Properties config;
    private final EPackage.Registry packageRegistry = new EPackageRegistryImpl(EPackage.Registry.INSTANCE);
    private Map<EClass, List<EClass>> descendants = new HashMap<>();

    public DBServer(String dbName, Properties config) {
        this.dbName = dbName;
        this.config = config;
    }

    public EPackage.Registry getPackageRegistry() {
        return packageRegistry;
    }

    public void registerEPackage(EPackage ePackage) {
        getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        for (EClassifier eClassifier : ePackage.getEClassifiers()) {
            if (eClassifier instanceof EClass) {
                EClass eClass = (EClass) eClassifier;
                if (!eClass.isAbstract()) {
                    for (EClass superType : eClass.getEAllSuperTypes()) {
                        getConcreteDescendants(superType).add(eClass);
                    }
                }
            }
        }
    }

    public List<EClass> getConcreteDescendants(EClass eClass) {
        return descendants.computeIfAbsent(eClass, (c) -> new ArrayList<EClass>() {
            {
                if (!eClass.isAbstract()) {
                    add(eClass);
                }
            }
        });
    }

    public String getTenantId() {
        return tenantId.get();
    }

    public void setTenantId(String tenantId) {
        DBServer.tenantId.set(tenantId);
    }

    public String getId(URI uri) {
        return uri.segmentCount() >= 1 ? uri.segment(0) : null;
    }

    public String getVersion(URI uri) {
        String query = uri.query();
        if (query == null || !query.contains("rev=")) {
            return null;
        }
        String versionStr = query.split("rev=", -1)[1];
        return StringUtils.isEmpty(versionStr) ? null : versionStr;
    }

    @Override
    public void close() throws Exception {
    }

    public Function<EClass, EStructuralFeature> getQualifiedNameDelegate() {
        return qualifiedNameDelegate;
    }

    public void setQualifiedNameDelegate(Function<EClass, EStructuralFeature> qualifiedNameDelegate) {
        this.qualifiedNameDelegate = qualifiedNameDelegate;
    }

    public Events getEvents() {
        return events;
    }

    protected Resource createResource(URI uri) {
        return new BinaryResourceImpl(uri);
    }

    protected abstract DBTransaction createDBTransaction(boolean readOnly, DBServer dbServer, String tenantId);

    private String createURIString(String id) {
        return getScheme() + "://" + dbName + "/" + (id != null ? id : "");
    }

    public URI createURI(String id) {
        return URI.createURI(createURIString(id));
    }

    public URI createURI(String id, String version) {
        return URI.createURI(String.format("%s?rev=%s", createURIString(id), version));
    }

    public String getQName(EObject eObject) {
        if (eObject instanceof EPackage) {
            EPackage ePackage = (EPackage) eObject;
            return ePackage.getNsURI();
        }
        EStructuralFeature sf = getQualifiedNameDelegate().apply(eObject.eClass());
        if (sf == null || !eObject.eIsSet(sf)) {
            throw new IllegalArgumentException(String.format("Can't get qName for eObject of class %s", EcoreUtil.getURI(eObject.eClass()).toString()));
        }
        return eObject.eGet(sf).toString();
    }

    public abstract String getScheme();

    public Properties getConfig() {
        return config;
    }

    public String getDbName() {
        return dbName;
    }

    public interface TxFunction<R> extends Serializable {
        R call(DBTransaction tx) throws Exception;
    }

    protected <R> R callWithTransaction(DBTransaction tx, TxFunction<R> f) throws Exception {
        return f.call(tx);
    }

    public <R> R inTransaction(boolean readOnly, TxFunction<R> f) throws Exception {
        return inTransaction(() -> createDBTransaction(readOnly, this, tenantId.get()), f);
    }

    public static class TxRetryStrategy {
        public int delay = 1;
        public int maxDelay = 1000;
        public int maxAttempts = 10;
        public List<Class<?>> retryClasses = new ArrayList<>();
    }

    protected TxRetryStrategy createTxRetryStrategy() {
        return new TxRetryStrategy();
    }

    public <R> R inTransaction(Supplier<DBTransaction> txSupplier, TxFunction<R> f) throws Exception {
        TxRetryStrategy retryStrategy = createTxRetryStrategy();
        int attempt = 1;
        int delay = retryStrategy.delay;
        while (true) {
            try {
                try (DBTransaction tx = txSupplier.get()) {
                    tx.begin();
                    try {
                        R result =  callWithTransaction(tx, f);
                        tx.commit();
                        return result;
                    }
                    catch (Throwable e) {
                        tx.rollback();
                        throw e;
                    }
                }
            }
            catch (Throwable e) {
                boolean retry = retryStrategy.retryClasses.stream().anyMatch(aClass -> aClass.isAssignableFrom(e.getClass()));
                if (!retry) {
                    throw e;
                }
                if (++attempt > retryStrategy.maxAttempts) {
                    throw e;
                }
                String message = e.getClass().getSimpleName() + ": " + e.getMessage() + " attempt no " + attempt + " after " + delay + "ms";
                logger.warn(message);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                }
                if (delay < retryStrategy.maxDelay) {
                    delay *= 2;
                }
            }
        }
    }
}
