package ru.neoflex.emf.hibernatedb;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.ecore.EPackage;
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
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class HBDBServer extends DBServer {
    private static final Logger logger = LoggerFactory.getLogger(HBDBServer.class);
    public static final String CONFIG_DEFAULT_SCHEMA = "emfdb.hb.defaultSchema";
    public static final String CONFIG_DRIVER = "emfdb.hb.driver";
    public static final String CONFIG_URL = "emfdb.hb.url";
    public static final String CONFIG_USER = "emfdb.hb.user";
    public static final String COMFIG_PASS = "emfdb.hb.pass";
    public static final String CONFIG_DIALECT = "emfdb.hb.dialect";
    public static final String CONFIG_SHOW_SQL = "emfdb.hb.show_sql";
    public static final String CONFIG_MIN_POOL_SIZE = "emfdb.hb.min_pool_size";
    public static final String CONFIG_MAX_POOL_SIZE = "emfdb.hb.max_pool_size";
    private final SessionFactory sessionFactory;
    private final Set<String> updatedSchemas = new HashSet<>();

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static class HBDBTenantIdentifierResolver implements CurrentTenantIdentifierResolver {
        @Override
        public String resolveCurrentTenantIdentifier() {
            return DBServer.tenantId.get();
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }

//    public static Function<String, String> setSchemaQuery = schema -> "SET schema " + schema;

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
            }
            catch ( SQLException e ) {
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

    public HBDBServer(String dbName, Properties config, List<EPackage> packages) {
        super(dbName, config, packages);
        Configuration configuration = getConfiguration();
        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties()).build();
        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        String defaultSchema = getConfig().getProperty(CONFIG_DEFAULT_SCHEMA, "");
        setSchema(defaultSchema);
    }

    public void setSchema(String schema) {
        setTenantId(schema);
        synchronized (updatedSchemas) {
            if (!updatedSchemas.contains(schema)) {
                Configuration configuration = getConfiguration();
                configuration.getProperties().put(Environment.DEFAULT_SCHEMA, schema);
                ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build();
                MetadataSources metadataSources = new MetadataSources( serviceRegistry );
                metadataSources.addAnnotatedClass(DBResource.class);
                MetadataBuilder metadataBuilder = metadataSources.getMetadataBuilder();
                MetadataImplementor metadata = (MetadataImplementor) metadataBuilder.build();
                SchemaUpdate schemaUpdate = new SchemaUpdate();
                schemaUpdate.execute(EnumSet.of(TargetType.DATABASE), metadata);
                updatedSchemas.add(getTenantId());
            }
        }
    }

    private Configuration getConfiguration() {
        Configuration configuration = new Configuration();
        Properties settings = new Properties();
        settings.put(Environment.DRIVER, getConfig().getProperty(CONFIG_DRIVER, "org.h2.Driver"));
        settings.put(Environment.URL, getConfig().getProperty(CONFIG_URL, "jdbc:h2:" + System.getProperty("user.home") + "/.h2home/" + this.dbName));
        settings.put(Environment.USER, getConfig().getProperty(CONFIG_USER, "sa"));
        settings.put(Environment.PASS, getConfig().getProperty(COMFIG_PASS, ""));
        settings.put(Environment.DIALECT, getConfig().getProperty(CONFIG_DIALECT, "org.hibernate.dialect.H2Dialect"));
        settings.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        settings.put(Environment.HBM2DDL_AUTO, "none");
        settings.put(Environment.SHOW_SQL, getConfig().getProperty(CONFIG_SHOW_SQL, "true"));
        settings.put(Environment.C3P0_MIN_SIZE, getConfig().getProperty(CONFIG_MIN_POOL_SIZE, "1"));
        settings.put(Environment.C3P0_MAX_SIZE, getConfig().getProperty(CONFIG_MAX_POOL_SIZE, "50"));
        settings.put(Environment.MULTI_TENANT, "SCHEMA");
        settings.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, HBDBTenantIdentifierResolver.class.getName());
        settings.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, HBDBConnectionProvider.class.getName());
        configuration.setProperties(settings);
        configuration.addAnnotatedClass(DBResource.class);
        return configuration;
    }

    public Session createSession() {
        return sessionFactory.openSession();
    }

    @Override
    protected DBTransaction createDBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        return new HBDBTransaction(readOnly, dbServer);
    }

    @Override
    public String getScheme() {
        return "hbdb";
    }

    @Override
    public void close() throws IOException {
        sessionFactory.close();
    }
}
