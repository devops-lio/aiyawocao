package com.killxdcj.aiyawocao.meta.manager;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class AliOSSBackendMetaManager extends MetaManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(AliOSSBackendMetaManager.class);

	private MetaManagerConfig config;
	private OSSClient ossClient;
	private Set<String> infohashMeta;
	private Thread infohashMetaSaver;
	private volatile long lastInfohashSize = 0;

	public AliOSSBackendMetaManager(MetaManagerConfig config) {
		super(null);
		this.config = config;
		ossClient = new OSSClient(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
		loadInfohashMeta();
		startInfohashMetaSaver();
	}

	public AliOSSBackendMetaManager(MetricRegistry metricRegistry, MetaManagerConfig config) {
		super(metricRegistry);
		this.config = config;
		ossClient = new OSSClient(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
		loadInfohashMeta();
		startInfohashMetaSaver();
		metricRegistry.register(MetricRegistry.name(MetaManager.class, "TotalMetaNumber"), (Gauge<Integer>)() -> infohashMeta.size());
	}

	@Override
	public void shutdown() {
		infohashMetaSaver.interrupt();
		saveInfohashMeta();
		ossClient.shutdown();
		LOGGER.info("alioss backend metamanager shutdowned");
	}

	@Override
	public boolean doesMetaExist(String infohash) {
		return doesMetaExist(infohash, false);
	}

	@Override
	public boolean doesMetaExist(String infohash, boolean forceCheckOSS) {
		infohash = infohash.toUpperCase();
		if (infohashMeta.contains(infohash)) {
			return true;
		}
		if (forceCheckOSS) {
			return ossClient.doesObjectExist(config.getBucketName(), buildBucketKey(infohash));
		} else {
			return false;
		}
	}

	@Override
	public void put(String infohash, byte[] meta) {
		infohash = infohash.toUpperCase();
		PutObjectResult result = ossClient.putObject(config.getBucketName(), buildBucketKey(infohash), new ByteArrayInputStream(meta));
		infohashMeta.add(infohash);
	}

	@Override
	public byte[] get(String infohash) throws MetaNotExistException, IOException {
		String key = buildBucketKey(infohash);
		if (!ossClient.doesObjectExist(config.getBucketName(), key)) {
			throw new MetaNotExistException("meta not exist : " + infohash);
		}
		InputStream in = ossClient.getObject(config.getBucketName(), key).getObjectContent();
		int length = in.available();
		byte[] meta = new byte[length];
		int idx = 0;
		while (length > 0) {
			int read = in.read(meta, idx, length);
			idx += read;
			length -= read;
		}
		return meta;
	}

	private String buildBucketKey(String infohash) {
		infohash = infohash.toUpperCase();
//		if (infohash.length() != 40) {
//			throw new InvalidInfohashException("invalid infohash : " + infohash);
//		}

		return "" + infohash.charAt(0) + infohash.charAt(1) + "/" +
				infohash.charAt(2) + infohash.charAt(3) + "/" +
				infohash.charAt(4) + infohash.charAt(5) + "/" +
				infohash;
	}

	private void loadInfohashMeta() {
		infohashMeta = new ConcurrentSkipListSet<>();
		if (ossClient.doesObjectExist(config.getBucketName(), config.getInfohashMetaKey())) {
			OSSObject ossObject = ossClient.getObject(config.getBucketName(), config.getInfohashMetaKey());
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(ossObject.getObjectContent()))) {
				String line = reader.readLine();
				while (line != null) {
					infohashMeta.add(line);
					line = reader.readLine();
				}
			} catch (Exception e) {
				throw new RuntimeException("load infohash meta error", e);
			}
		}
		lastInfohashSize = infohashMeta.size();
		LOGGER.info("infohash meta loaded, {}, size:{}", config.getInfohashMetaKey(), infohashMeta.size());
	}

	private synchronized void saveInfohashMeta() {
		if (infohashMeta == null) {
			return;
		}

		long newSize = infohashMeta.size();
		if (newSize != lastInfohashSize) {
			String meta = String.join("\n", infohashMeta);
			ossClient.putObject(config.getBucketName(), config.getInfohashMetaKey(), new ByteArrayInputStream(meta.getBytes()));
			lastInfohashSize = newSize;
			LOGGER.info("infohash meta saved, {}, size:{}", config.getInfohashMetaKey(), lastInfohashSize);
		}
	}

	private void startInfohashMetaSaver() {
		infohashMetaSaver = new Thread(new Runnable() {
			@Override
			public void run() {
				Thread.currentThread().setName("Infohash Meta Saver Thread");
				try {
					while (true) {
						Thread.sleep(10 * 60 * 1000);
						saveInfohashMeta();
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		});
		infohashMetaSaver.start();
	}

	private void appendLine(String bucketKey, String line) {
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentType("text/plain");
//		AppendObjectRequest appendObjectRequest = new AppendObjectRequest(bucketName, "")
	}
}
