package com.killxdcj.aiyawocao.meta.crawler.config;

import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MetaCrawlerConfig {
	private long metaFetchTimeout = 5 * 60 * 1000;
	private int infohashMaxConcurrentFetch = 5;
	private int nodeMaxConcurrentFetch = 5;
	private String influxdbAddr = "example-influxdb:port";
	private String influxdbUser = "example-influxdb-user";
	private String influxdbPassword = "example-influxdb-user";
	private String influxdbName = "example-influxdb-name";
	private boolean useNIOMetaFetcher = true;
	private BittorrentConfig bittorrentConfig;
	private MetaManagerConfig metaManagerConfig;

	public MetaCrawlerConfig() {
	}

	public MetaCrawlerConfig(long metaFetchTimeout, int infohashMaxConcurrentFetch, int nodeMaxConcurrentFetch,
													 String influxdbAddr, String influxdbUser, String influxdbPassword, String influxdbName,
													 boolean useNIOMetaFetcher, BittorrentConfig bittorrentConfig, MetaManagerConfig metaManagerConfig) {
		this.metaFetchTimeout = metaFetchTimeout;
		this.infohashMaxConcurrentFetch = infohashMaxConcurrentFetch;
		this.nodeMaxConcurrentFetch = nodeMaxConcurrentFetch;
		this.influxdbAddr = influxdbAddr;
		this.influxdbUser = influxdbUser;
		this.influxdbPassword = influxdbPassword;
		this.influxdbName = influxdbName;
		this.useNIOMetaFetcher = useNIOMetaFetcher;
		this.bittorrentConfig = bittorrentConfig;
		this.metaManagerConfig = metaManagerConfig;
	}

	public long getMetaFetchTimeout() {
		return metaFetchTimeout;
	}

	public void setMetaFetchTimeout(long metaFetchTimeout) {
		this.metaFetchTimeout = metaFetchTimeout;
	}

	public BittorrentConfig getBittorrentConfig() {
		return bittorrentConfig;
	}

	public int getInfohashMaxConcurrentFetch() {
		return infohashMaxConcurrentFetch;
	}

	public void setInfohashMaxConcurrentFetch(int infohashMaxConcurrentFetch) {
		this.infohashMaxConcurrentFetch = infohashMaxConcurrentFetch;
	}

	public int getNodeMaxConcurrentFetch() {
		return nodeMaxConcurrentFetch;
	}

	public void setNodeMaxConcurrentFetch(int nodeMaxConcurrentFetch) {
		this.nodeMaxConcurrentFetch = nodeMaxConcurrentFetch;
	}

	public void setBittorrentConfig(BittorrentConfig bittorrentConfig) {
		this.bittorrentConfig = bittorrentConfig;
	}

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

	public boolean getUseNIOMetaFetcher() {
		return useNIOMetaFetcher;
	}

	public void setUseNIOMetaFetcher(boolean useNIOMetaFetcher) {
		this.useNIOMetaFetcher = useNIOMetaFetcher;
	}

	@Override
	public String toString() {
		return "MetaCrawlerConfig{" +
						"metaFetchTimeout=" + metaFetchTimeout +
						", infohashMaxConcurrentFetch=" + infohashMaxConcurrentFetch +
						", nodeMaxConcurrentFetch=" + nodeMaxConcurrentFetch +
						", influxdbAddr='" + influxdbAddr + '\'' +
						", influxdbUser='" + influxdbUser + '\'' +
						", influxdbPassword='" + influxdbPassword + '\'' +
						", influxdbName='" + influxdbName + '\'' +
						", useNIOMetaFetcher=" + useNIOMetaFetcher +
						", bittorrentConfig=" + bittorrentConfig +
						", metaManagerConfig=" + metaManagerConfig +
						'}';
	}

	public static MetaCrawlerConfig fromYamlString(String yamlConf) {
		Yaml yaml = new Yaml();
		return yaml.loadAs(yamlConf, MetaCrawlerConfig.class);
	}

	public static MetaCrawlerConfig fromYamlConfFile(String confFile) throws FileNotFoundException {
		Yaml yaml = new Yaml();
		return yaml.loadAs(new FileInputStream(confFile), MetaCrawlerConfig.class);
	}

	public String toYamlConf() {
		Yaml yaml = new Yaml();
		return yaml.dumpAsMap(this);
	}

	public static void main(String[] args) {
		MetaCrawlerConfig metaCrawlerConfig = new MetaCrawlerConfig();
		metaCrawlerConfig.setBittorrentConfig(new BittorrentConfig());
		metaCrawlerConfig.setMetaManagerConfig(new MetaManagerConfig());
		String yamlConf = metaCrawlerConfig.toYamlConf();
		System.out.println(yamlConf);
		System.out.println(MetaCrawlerConfig.fromYamlString(yamlConf).toString());
	}
}
