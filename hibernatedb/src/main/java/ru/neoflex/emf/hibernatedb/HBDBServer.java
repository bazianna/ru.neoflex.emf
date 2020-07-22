package ru.neoflex.emf.hibernatedb;

import org.eclipse.emf.ecore.EPackage;
import org.hibernate.HibernateException;
import org.hibernate.Metamodel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

public class HBDBServer extends DBServer {
    private static final Logger logger = LoggerFactory.getLogger(HBDBServer.class);
    private final SessionFactory sessionFactory;

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

    public static Function<String, String> setSchemaQuery = schema -> "SET schema " + schema;

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
                connection.createStatement().execute( setSchemaQuery.apply(tenantIdentifier) );
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

    public HBDBServer(List<EPackage> packages, String dbName) {
        super(packages, dbName);
        Configuration configuration = new Configuration();
        Properties settings = new Properties();
        settings.put(Environment.DRIVER, "org.h2.Driver");
        settings.put(Environment.URL, "jdbc:h2:" + System.getProperty("user.home") + "/.h2home/" + dbName);
        settings.put(Environment.USER, "sa");
        settings.put(Environment.PASS, "");
        settings.put(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");
        settings.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        settings.put(Environment.HBM2DDL_AUTO, "update");
        settings.put(Environment.SHOW_SQL, "true");
        settings.put(Environment.C3P0_MIN_SIZE, "1");
        settings.put(Environment.C3P0_MAX_SIZE, "500");
        settings.put(Environment.MULTI_TENANT, "SCHEMA");
        settings.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, HBDBTenantIdentifierResolver.class.getName());
        settings.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, HBDBConnectionProvider.class.getName());
        configuration.setProperties(settings);
        configuration.addAnnotatedClass(DBResource.class);
        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties()).build();
        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        Metamodel metamodel = sessionFactory.getMetamodel();
        metamodel.getEntities().forEach(entityType -> {
            logger.info("Entity: " + entityType.getName());
        });
    }

    public Session createSession() {
        return sessionFactory.openSession();
    }

    @Override
    protected DBTransaction createDBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        return new HBDBTransaction(readOnly, dbServer, tenantId);
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
