package com.killxdcj.aiyawocao.meta.manager.config;

import org.yaml.snakeyaml.Yaml;

public class MetaManagerConfig {
	private String bucketName = "bittorrent-meta";
	private String infohashMetaKey = "infohash-meta";
	private String endpoint = "http://oss-cn-shenzhen.aliyuncs.com";
	private String accessKeyId = "example-accesskey-id";
	private String accessKeySecret = "example-accesskey-secret";
	private String metaCentreAddr = "127.0.0.1:10241";

	public MetaManagerConfig() {
	}

	public String getBucketName() {
		return bucketName;
	}

	public void setBucketName(String bucketName) {
		this.bucketName = bucketName;
	}

	public String getInfohashMetaKey() {
		return infohashMetaKey;
	}

	public void setInfohashMetaKey(String infohashMetaKey) {
		this.infohashMetaKey = infohashMetaKey;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getAccessKeyId() {
		return accessKeyId;
	}

	public void setAccessKeyId(String accessKeyId) {
		this.accessKeyId = accessKeyId;
	}

	public String getAccessKeySecret() {
		return accessKeySecret;
	}

	public void setAccessKeySecret(String accessKeySecret) {
		this.accessKeySecret = accessKeySecret;
	}

	public String getMetaCentreAddr() {
		return metaCentreAddr;
	}

	public void setMetaCentreAddr(String metaCentreAddr) {
		this.metaCentreAddr = metaCentreAddr;
	}

	@Override
	public String toString() {
		return "MetaManagerConfig{" +
				"bucketName='" + bucketName + '\'' +
				", infohashMetaKey='" + infohashMetaKey + '\'' +
				", endpoint='" + endpoint + '\'' +
				", accessKeyId='" + accessKeyId + '\'' +
				", accessKeySecret='" + accessKeySecret + '\'' +
				", metaCentreAddr='" + metaCentreAddr + '\'' +
				'}';
	}

	public static MetaManagerConfig fromYamlConf(String yamlConf) {
		Yaml yaml = new Yaml();
		return yaml.loadAs(yamlConf, MetaManagerConfig.class);
	}

	public String toYamlConf() {
		Yaml yaml = new Yaml();
		return yaml.dumpAsMap(this);
	}

	public static void main(String[] args) {
		MetaManagerConfig metaManagerConfig = new MetaManagerConfig();
		String yamlConf = metaManagerConfig.toYamlConf();
		System.out.println(yamlConf);
		System.out.println(MetaManagerConfig.fromYamlConf(yamlConf).toString());
	}
}
