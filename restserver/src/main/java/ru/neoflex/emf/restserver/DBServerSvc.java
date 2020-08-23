package ru.neoflex.emf.restserver;

import org.springframework.stereotype.Service;
import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.gitdb.GitDBFactory;

import javax.annotation.PostConstruct;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

@Service
public class DBServerSvc {
    private DBServer dbServer;

    @PostConstruct
    public void init() throws Exception {
        Properties properties = System.getProperties();
        String dbName = properties.getProperty("db-name", "emfdb");
        String dbType = properties.getProperty("db-type", GitDBFactory.class.getName());
        ServiceLoader<DBFactory> loader = ServiceLoader.load(DBFactory.class);
        DBFactory dbFactory = StreamSupport.stream(loader.spliterator(), false)
                .filter(f->f.getClass().getName().equals(dbType))
                .findFirst().get();
        dbServer = dbFactory.create(dbName, properties);
    }

    public DBServer getDbServer() {
        return dbServer;
    }
}
