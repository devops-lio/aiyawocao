package com.killxdcj.aiyawocao.meta.centre.config;

import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Repository;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@Repository("metaCentreConfig")
public class MetaCentreConfig implements InitializingBean {
    private String influxdbAddr = "example-influxdb:port";
    private String influxdbUser = "example-influxdb-user";
    private String influxdbPassword = "example-influxdb-user";
    private String influxdbName = "example-influxdb-name";
    private String cluster = "default";
    private MetaManagerConfig metaManagerConfig;

    public MetaManagerConfig getMetaManagerConfig() {
        return metaManagerConfig;
    }

    public void setMetaManagerConfig(MetaManagerConfig metaManagerConfig) {
        this.metaManagerConfig = metaManagerConfig;
    }

    public String getInfluxdbAddr() {
        return influxdbAddr;
    }

    public void setInfluxdbAddr(String influxdbAddr) {
        this.influxdbAddr = influxdbAddr;
    }

    public String getInfluxdbUser() {
        return influxdbUser;
    }

    public void setInfluxdbUser(String influxdbUser) {
        this.influxdbUser = influxdbUser;
    }

    public String getInfluxdbPassword() {
        return influxdbPassword;
    }

    public void setInfluxdbPassword(String influxdbPassword) {
        this.influxdbPassword = influxdbPassword;
    }

    public String getInfluxdbName() {
        return influxdbName;
    }

    public void setInfluxdbName(String influxdbName) {
        this.influxdbName = influxdbName;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    @Override
    public String toString() {
        return "MetaCentreConfig{" +
                "metaManagerConfig=" + metaManagerConfig +
                '}';
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String conf = System.getProperty("conf", "./conf/meta-centre.yaml");
        MetaCentreConfig config = MetaCentreConfig.fromYamlConfFile(conf);
        this.metaManagerConfig = config.getMetaManagerConfig();
        this.influxdbAddr = config.getInfluxdbAddr();
        this.influxdbName = config.getInfluxdbName();
        this.influxdbUser = config.getInfluxdbUser();
        this.influxdbPassword = config.getInfluxdbPassword();
    }

    private String toYamlString() {
        Yaml yaml = new Yaml();
        return yaml.dumpAsMap(this);
    }

    private static MetaCentreConfig fromYamlConfFile(String confFile) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        return yaml.loadAs(new FileInputStream(confFile), MetaCentreConfig.class);
    }

    public static MetaCentreConfig fromYamlString(String yamlStr) {
        Yaml yaml = new Yaml();
        return yaml.loadAs(yamlStr, MetaCentreConfig.class);
    }

    public static void main(String[] args) {
        MetaCentreConfig config = new MetaCentreConfig();
        config.setMetaManagerConfig(new MetaManagerConfig());
        System.out.println(config.toYamlString());
    }
}
