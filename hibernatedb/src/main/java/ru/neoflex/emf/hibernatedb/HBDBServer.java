package ru.neoflex.emf.hibernatedb;

import org.eclipse.emf.ecore.EPackage;
import org.hibernate.Metamodel;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class HBDBServer extends DBServer {
    private static final Logger logger = LoggerFactory.getLogger(HBDBServer.class);
    private SessionFactory sessionFactory;

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
        settings.put(Environment.C3P0_MAX_SIZE, "5");
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
    protected DBTransaction createDBTransaction(boolean readOnly) {
        return null;
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
