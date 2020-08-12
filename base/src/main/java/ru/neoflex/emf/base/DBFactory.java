package ru.neoflex.emf.base;

import java.util.Properties;

public interface DBFactory {
    DBServer create(String name, Properties properties) throws Exception;
}
