package ru.neoflex.emf.gitdb;

import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.base.DBServer;

import java.util.Properties;

public class GitDBFactory implements DBFactory {
    @Override
    public DBServer create(String name, Properties properties) {
        return new GitDBServer(name, properties);
    }
}
