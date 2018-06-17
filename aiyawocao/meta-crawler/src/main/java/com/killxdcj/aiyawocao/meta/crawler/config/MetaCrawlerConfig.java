package com.killxdcj.aiyawocao.meta.crawler.config;

import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MetaCrawlerConfig {
	private long metaFetchTimeout = 10 * 60 * 1000;
	private BittorrentConfig bittorrentConfig;
	private MetaManagerConfig metaManagerConfig;

	public MetaCrawlerConfig() {
	}

	public MetaCrawlerConfig(long metaFetchTimeout, BittorrentConfig bittorrentConfig, MetaManagerConfig metaManagerConfig) {
		this.metaFetchTimeout = metaFetchTimeout;
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

	public void setBittorrentConfig(BittorrentConfig bittorrentConfig) {
		this.bittorrentConfig = bittorrentConfig;
	}

	public MetaManagerConfig getMetaManagerConfig() {
		return metaManagerConfig;
	}

	public void setMetaManagerConfig(MetaManagerConfig metaManagerConfig) {
		this.metaManagerConfig = metaManagerConfig;
	}

	@Override
	public String toString() {
		return "MetaCrawlerConfig{" +
						"metaFetchTimeout=" + metaFetchTimeout +
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
