package ru.neoflex.emf.hibernatedb;

import ru.neoflex.emf.base.DBFactory;
import ru.neoflex.emf.base.DBServer;

import java.util.Properties;

public class HBDBFactory implements DBFactory {
    @Override
    public DBServer create(String name, Properties properties) {
        return new HBDBServer(name, properties);
    }
}
