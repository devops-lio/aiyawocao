package com.killxdcj.aiyawocao.meta.manager;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.*;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

public class AliOSSBackendMetaManager extends MetaManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(AliOSSBackendMetaManager.class);

	private MetaManagerConfig config;
	private OSSClient ossClient;
	private Set<String> allIndex;
	private Set<String> splitedIndex;
	private Thread infohashMetaSaver;
	private volatile int lastSplitedIndexSize = 0;
	private volatile int archivedSize = 0;

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
		metricRegistry.register(MetricRegistry.name(MetaManager.class, "TotalMetaNumber"),
			(Gauge<Integer>) () -> archivedSize + splitedIndex.size());
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
		if (allIndex.contains(infohash)) {
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
		allIndex.add(infohash);
		splitedIndex.add(infohash);
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
		allIndex = new ConcurrentSkipListSet<>();
		splitedIndex = new ConcurrentSkipListSet<>();

		ListObjectsRequest request = new ListObjectsRequest(config.getBucketName()).withPrefix(config.getIndexRoot())
			.withMaxKeys(1000);
		ObjectListing objs = ossClient.listObjects(request);
		LOGGER.info("loading bittorrent-meta index, size:{}", objs.getObjectSummaries().size());
		for (OSSObjectSummary summary : ossClient.listObjects(request).getObjectSummaries()) {
			String indexFile = summary.getKey();
			boolean isLatest = indexFile.equals(config.getIndexRoot() + "/" + config.getIndexPrefix());
			OSSObject ossObject = ossClient.getObject(config.getBucketName(), indexFile);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(ossObject.getObjectContent()))) {
				String line = reader.readLine();
				while (line != null) {
					allIndex.add(line);
					if (isLatest) {
						splitedIndex.add(line);
					}
					line = reader.readLine();
				}
				LOGGER.info("loaded bittorrent-meta splited index file, {}", indexFile);
			} catch (Exception e) {
				throw new RuntimeException("load infohash meta error", e);
			}
		}
		lastSplitedIndexSize = splitedIndex.size();
		int all = allIndex.size();
		archivedSize = all - lastSplitedIndexSize;
		LOGGER.info("infohash meta loaded, {}, allSize:{}, splitedSize:{}", config.getIndexRoot(), all,
			lastSplitedIndexSize);
	}

	private synchronized void saveInfohashMeta() {
		if (splitedIndex == null) {
			return;
		}

		int newSize = splitedIndex.size();
		if (newSize == lastSplitedIndexSize) {
			return;
		}

		LOGGER.info("start save infohash meta, {} -> {}", lastSplitedIndexSize, newSize);

		Set<String> old = splitedIndex;
		try {
			String indexFile = config.getIndexRoot() + "/" + config.getIndexPrefix();
			if (newSize > config.getMaxIndexSize()) {
				SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
				indexFile = indexFile + fmt.format(new Date());
				splitedIndex = new ConcurrentSkipListSet<>();
				archivedSize += newSize;
			}

			String index = String.join("\n", old);
			ossClient.putObject(config.getBucketName(), indexFile, new ByteArrayInputStream(index.getBytes()));
			LOGGER.info("infohash meta saved, {}, size:{}", indexFile, newSize);

			if (newSize > config.getMaxIndexSize()) {
				String nexIndex = String.join("\n", splitedIndex);
				ossClient.putObject(config.getBucketName(), config.getIndexRoot() + "/" + config.getIndexPrefix(),
								new ByteArrayInputStream(nexIndex.getBytes()));
				LOGGER.info("infohash splited");
			}
		} catch (Throwable t) {
			LOGGER.error("sava index error", t);
			splitedIndex.addAll(old);
		} finally {
			lastSplitedIndexSize = splitedIndex.size();
		}
	}

	private void startInfohashMetaSaver() {
		infohashMetaSaver = new Thread(new Runnable() {
			@Override
			public void run() {
				Thread.currentThread().setName("Infohash Meta Saver Thread");
				while (true) {
					try {
						Thread.sleep(10 * 60 * 1000);
						saveInfohashMeta();
					} catch (InterruptedException e) {
						return;
					} catch (Throwable e) {
						LOGGER.error("save infohash meta error", e);
					}
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
