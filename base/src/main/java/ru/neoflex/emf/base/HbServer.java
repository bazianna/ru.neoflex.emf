package ru.neoflex.emf.base;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class HbServer implements AutoCloseable {
    public static final String CONFIG_DEFAULT_SCHEMA = "emfdb.hb.defaultSchema";
    public static final String CONFIG_DRIVER = "emfdb.hb.driver";
    public static final String CONFIG_URL = "emfdb.hb.url";
    public static final String CONFIG_USER = "emfdb.hb.user";
    public static final String COMFIG_PASS = "emfdb.hb.pass";
    public static final String CONFIG_DIALECT = "emfdb.hb.dialect";
    public static final String CONFIG_SHOW_SQL = "emfdb.hb.show_sql";
    public static final String CONFIG_MIN_POOL_SIZE = "emfdb.hb.min_pool_size";
    public static final String CONFIG_MAX_POOL_SIZE = "emfdb.hb.max_pool_size";
    private static final Logger logger = LoggerFactory.getLogger(HbServer.class);
    protected static final ThreadLocal<String> tenantId = new InheritableThreadLocal<>();

    public Function<EAttribute, Boolean> getIndexedAttributeDelegate() {
        return indexedAttributeDelegate;
    }

    public void setIndexedAttributeDelegate(Function<EAttribute, Boolean> indexedAttributeDelegate) {
        this.indexedAttributeDelegate = indexedAttributeDelegate;
    }

    public static class DBObjectHandle {
        Long id;
        Integer version;
    }

    private static final ThreadLocal<Map<EObject, DBObjectHandle>> eObjectToIdMap = new ThreadLocal<>();
    protected final SessionFactory sessionFactory;
    private final String dbName;
    private final Events events = new Events();
    private final Set<String> updatedSchemas = new HashSet<>();
    private Function<EClass, EStructuralFeature> qualifiedNameDelegate = eClass -> eClass.getEStructuralFeature("name");
    private Function<EAttribute, Boolean> indexedAttributeDelegate = eAttribute -> eAttribute.getEAttributeType() == EcorePackage.eINSTANCE.getEString();
    private final Properties config;
    private final EPackage.Registry packageRegistry = new EPackageRegistryImpl(EPackage.Registry.INSTANCE);
    private Map<EClass, List<EClass>> descendants = new HashMap<>();

    public HbServer(String dbName, Properties config) {
        this.dbName = dbName;
        this.config = config;
        packageRegistry.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        packageRegistry.put(GenModelPackage.eNS_URI, GenModelPackage.eINSTANCE);
        Configuration configuration = getConfiguration();
        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties()).build();
        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        String defaultSchema = getConfig().getProperty(CONFIG_DEFAULT_SCHEMA, "");
        setSchema(defaultSchema);
    }

    public Map<EObject, HbServer.DBObjectHandle> getEObjectToIdMap() {
        if (eObjectToIdMap.get() == null) {
            eObjectToIdMap.set(new WeakHashMap<>());
        }
        return eObjectToIdMap.get();
    }

    public Long getId(EObject eObject) {
        HbServer.DBObjectHandle handle = getEObjectToIdMap().get(eObject);
        return handle != null ? handle.id : null;
    }

    public void setId(EObject eObject, Long id) {
        HbServer.DBObjectHandle handle = getEObjectToIdMap().get(eObject);
        if (handle == null) {
            handle = new HbServer.DBObjectHandle();
            getEObjectToIdMap().put(eObject, handle);
        }
        handle.id = id;
    }

    public Integer getVersion(EObject eObject) {
        HbServer.DBObjectHandle handle = getEObjectToIdMap().get(eObject);
        return handle != null ? handle.version : null;
    }

    public void setVersion(EObject eObject, Integer version) {
        HbServer.DBObjectHandle handle = getEObjectToIdMap().get(eObject);
        if (handle == null) {
            handle = new HbServer.DBObjectHandle();
            getEObjectToIdMap().put(eObject, handle);
        }
        handle.version = version;
    }

    public List<EPackage> loadDynamicPackages() throws Exception {
        return inTransaction(true, tx -> {
            ResourceSet resourceSet = tx.getResourceSet();
            return tx.findByClass(resourceSet, EcorePackage.Literals.EPACKAGE)
                    .flatMap(resource -> resource.getContents().stream())
                    .map(eObject -> (EPackage) eObject)
                    .collect(Collectors.toList());
        });
    }

    public void registerDynamicPackages() throws Exception {
        loadDynamicPackages().forEach(this::registerEPackage);
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
        HbServer.tenantId.set(tenantId);
    }

    public Long getId(URI uri) {
        if (uri.segmentCount() >= 1) {
            return Long.parseLong(uri.segment(0));
        }
        return null;
    }

    public boolean canHandle(URI uri) {
        return getScheme().equals(uri.scheme()) && Objects.equals(uri.authority(), getDbName());
    }

    public Integer getVersion(URI uri) {
        String query = uri.query();
        if (query == null || !query.contains("rev=")) {
            return null;
        }
        String versionStr = query.split("rev=", -1)[1];
        return StringUtils.isEmpty(versionStr) ? null : Integer.parseInt(versionStr);
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
        return new HbResource(uri);
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSchema(String schema) {
        setTenantId(schema);
        synchronized (updatedSchemas) {
            if (!updatedSchemas.contains(schema)) {
                Configuration configuration = getConfiguration();
                configuration.getProperties().put(Environment.DEFAULT_SCHEMA, schema);
                ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build();
                MetadataSources metadataSources = new MetadataSources(serviceRegistry);
                metadataSources.addAnnotatedClass(DBObject.class);
                MetadataBuilder metadataBuilder = metadataSources.getMetadataBuilder();
                MetadataImplementor metadata = (MetadataImplementor) metadataBuilder.build();
                SchemaUpdate schemaUpdate = new SchemaUpdate();
                schemaUpdate.execute(EnumSet.of(TargetType.DATABASE), metadata);
                updatedSchemas.add(getTenantId());
            }
        }
    }

    protected Configuration getConfiguration() {
        Configuration configuration = new Configuration();
        Properties settings = new Properties();
        settings.put(Environment.DRIVER, getConfig().getProperty(CONFIG_DRIVER, "org.h2.Driver"));
        settings.put(Environment.URL, getConfig().getProperty(CONFIG_URL, "jdbc:h2:" + System.getProperty("user.home") + "/.h2home/" + this.getDbName()));
        settings.put(Environment.USER, getConfig().getProperty(CONFIG_USER, "sa"));
        settings.put(Environment.PASS, getConfig().getProperty(COMFIG_PASS, ""));
        settings.put(Environment.DIALECT, getConfig().getProperty(CONFIG_DIALECT, "org.hibernate.dialect.H2Dialect"));
        settings.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        settings.put(Environment.HBM2DDL_AUTO, "none");
        settings.put(Environment.SHOW_SQL, getConfig().getProperty(CONFIG_SHOW_SQL, "false"));
        settings.put(Environment.C3P0_MIN_SIZE, getConfig().getProperty(CONFIG_MIN_POOL_SIZE, "1"));
        settings.put(Environment.C3P0_MAX_SIZE, getConfig().getProperty(CONFIG_MAX_POOL_SIZE, "50"));
        settings.put(Environment.MULTI_TENANT, "SCHEMA");
        settings.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, DBTenantIdentifierResolver.class.getName());
        settings.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, HBDBConnectionProvider.class.getName());
        configuration.setProperties(settings);
        configuration.addAnnotatedClass(DBObject.class);
        return configuration;
    }

    public Session createSession() {
        return sessionFactory.openSession();
    }

    protected HbTransaction createDBTransaction(boolean readOnly) {
        return new HbTransaction(readOnly, this);
    }

    private String createURIString(Long id, Integer version) {
        return getScheme() + "://" + dbName + "/" + (id != null ? id : "") + (version != null ? ("?rev=" + version) : "");
    }

    public URI createURI() {
        return createURI((Long)null);
    }

    public URI createURI(EObject eObject) {
        return createURI(getId(eObject), getVersion(eObject));
    }

    public URI createURI(Long id) {
        return URI.createURI(createURIString(id, null));
    }

    public URI createURI(Long id, Integer version) {
        return URI.createURI(createURIString(id, version));
    }

    public EStructuralFeature getQNameSF(EClass eClass) {
        EStructuralFeature sf;
        if (EcorePackage.Literals.EPACKAGE == eClass) {
            sf = EcorePackage.Literals.EPACKAGE__NS_URI;
        } else {
            sf = getQualifiedNameDelegate().apply(eClass);
        }
        return sf;
    }

    public String getQName(EObject eObject) {
        EStructuralFeature sf = getQNameSF(eObject.eClass());
        return sf != null ? eObject.eGet(sf).toString() : null;
    }

    public String getScheme() {
        return "hbdb";
    }

    public Properties getConfig() {
        return config;
    }

    public String getDbName() {
        return dbName;
    }

    @Override
    public void close() {
        sessionFactory.close();
    }

    public interface TxFunction<R> extends Serializable {
        R call(HbTransaction tx) throws Exception;
    }

    protected <R> R callWithTransaction(HbTransaction tx, TxFunction<R> f) throws Exception {
        return f.call(tx);
    }

    public <R> R inTransaction(boolean readOnly, TxFunction<R> f) throws Exception {
        return inTransaction(() -> createDBTransaction(readOnly), f);
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

    public <R> R inTransaction(Supplier<HbTransaction> txSupplier, TxFunction<R> f) throws Exception {
        TxRetryStrategy retryStrategy = createTxRetryStrategy();
        int attempt = 1;
        int delay = retryStrategy.delay;
        while (true) {
            try {
                try (HbTransaction tx = txSupplier.get()) {
                    tx.begin();
                    try {
                        R result = callWithTransaction(tx, f);
                        tx.commit();
                        return result;
                    } catch (Throwable e) {
                        tx.rollback();
                        throw e;
                    }
                }
            } catch (Throwable e) {
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

    public static class DBTenantIdentifierResolver implements CurrentTenantIdentifierResolver {
        @Override
        public String resolveCurrentTenantIdentifier() {
            return HbServer.tenantId.get();
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }

    public static class HBDBConnectionProvider extends C3P0ConnectionProvider implements MultiTenantConnectionProvider {
        @Override
        public Connection getAnyConnection() throws SQLException {
            return super.getConnection();
        }

        @Override
        public void releaseAnyConnection(Connection connection) throws SQLException {
            super.closeConnection(connection);
        }

        @Override
        public Connection getConnection(String tenantIdentifier) throws SQLException {
            final Connection connection = getAnyConnection();
            try {
                if (StringUtils.isNoneEmpty(tenantIdentifier)) {
//                    connection.createStatement().execute( setSchemaQuery.apply(tenantIdentifier) );
                    connection.setSchema(tenantIdentifier);
                }
            } catch (SQLException e) {
                throw new HibernateException(
                        "Could not alter JDBC connection to specified schema [" + tenantIdentifier + "]", e);
            }
            return connection;
        }

        @Override
        public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
            super.closeConnection(connection);
        }
    }
}
