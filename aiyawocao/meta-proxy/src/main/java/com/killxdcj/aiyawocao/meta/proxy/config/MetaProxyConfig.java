package com.killxdcj.aiyawocao.meta.proxy.config;

import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MetaProxyConfig {
	private int port = 10241;
	private int maxContentLength = 10 * 1024 * 1024;
	private String influxdbAddr = "example-influxdb:port";
	private String influxdbUser = "example-influxdb-user";
	private String influxdbPassword = "example-influxdb-user";
	private String influxdbName = "example-influxdb-name";
	private String cluster = "default";
	private MetaManagerConfig metaManagerConfig;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public MetaManagerConfig getMetaManagerConfig() {
		return metaManagerConfig;
	}

	public void setMetaManagerConfig(MetaManagerConfig metaManagerConfig) {
		this.metaManagerConfig = metaManagerConfig;
	}

	public int getMaxContentLength() {
		return maxContentLength;
	}

	public void setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
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
		return "MetaProxyConfig{" +
				"port=" + port +
				", maxContentLength=" + maxContentLength +
				", influxdbAddr='" + influxdbAddr + '\'' +
				", influxdbUser='" + influxdbUser + '\'' +
				", influxdbPassword='" + influxdbPassword + '\'' +
				", influxdbName='" + influxdbName + '\'' +
				", metaManagerConfig=" + metaManagerConfig +
				'}';
	}

	public static MetaProxyConfig fromYamlConf(String yamlConf) throws FileNotFoundException {
		Yaml yaml = new Yaml();
		return yaml.loadAs(new FileInputStream(yamlConf), MetaProxyConfig.class);
	}

	private String toYamlString() {
		Yaml yaml = new Yaml();
		return yaml.dumpAsMap(this);
	}

	public static void main(String[] args) {
		MetaProxyConfig config = new MetaProxyConfig();
		System.out.println(config.toYamlString());
	}
}
