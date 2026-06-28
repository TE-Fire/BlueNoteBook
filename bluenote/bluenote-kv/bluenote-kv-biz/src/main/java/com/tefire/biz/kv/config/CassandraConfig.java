package com.tefire.biz.kv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

public class CassandraConfig extends AbstractCassandraConfiguration {
    
    @Value("${spring.cassandra.keyspace-name}")
    private String keySpace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    @Override
    protected String getKeyspaceName() {
        return keySpace;
    }

    @Override
    protected String getContactPoints() {
        return contactPoints;
    }

    @Override
    protected int getPort() {
        return port;
    }
}
