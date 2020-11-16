package ru.neoflex.emf.base;

import java.util.Properties;

public class DBFactory {
    DBServer create(String name, Properties properties) throws Exception {
        return new DBServer(name, properties);
    }
}
