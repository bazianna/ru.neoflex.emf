package ru.neoflex.emf.restserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Service;
import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.gitdb.GitDBFactory;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

@Service
public class DBServerSvc {
    @Autowired
    Environment env;
    private DBServer dbServer;

    @PostConstruct
    public void init() throws Exception {
        Properties props = new Properties();
        MutablePropertySources propSrcs = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::<String>stream)
                .forEach(propName -> props.setProperty(propName, env.getProperty(propName)));
        String dbName = props.getProperty("db-name", "emfdb");
        String dbType = props.getProperty("db-type", GitDBFactory.class.getName());
        ServiceLoader<DBFactory> loader = ServiceLoader.load(DBFactory.class);
        DBFactory dbFactory = StreamSupport.stream(loader.spliterator(), false)
                .filter(f->f.getClass().getName().equals(dbType))
                .findFirst().get();
        dbServer = dbFactory.create(dbName, props);
    }

    public DBServer getDbServer() {
        return dbServer;
    }
}
