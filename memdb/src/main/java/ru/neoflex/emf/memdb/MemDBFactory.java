package ru.neoflex.emf.memdb;

import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.base.DBServer;

import java.util.Properties;

public class MemDBFactory implements DBFactory {
    @Override
    public DBServer create(String name, Properties properties) throws Exception {
        return new MemDBServer(name, properties);
    }
}
