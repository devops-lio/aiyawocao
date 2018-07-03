package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.meta.manager.AliOSSBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.MetaCentreBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) {
        System.out.println( "Hello World!" );
//        App.testOSS();
        for (int i = 0; i < 10000; i++) {
            App.testProxy();
            System.out.println("test ----------------> " + i);
        }
    }

    public static void testOSS() {
        try {
            AliOSSBackendMetaManager metacenter = new AliOSSBackendMetaManager(new MetricRegistry(), new MetaManagerConfig());

            System.out.println(metacenter.doesMetaExist("00000883D6E40593F6ABADE71D74C5E8A44AD582"));
            System.out.println(metacenter.doesMetaExist("00000883D6E40593F6ABADE71D74C5E8A44AD582"));
            System.out.println(metacenter.doesMetaExist("00000883D6E40593F6ABADE71D74C5E8A44AD582"));
            metacenter.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testProxy() {
        try {
            MetaManagerConfig config = new MetaManagerConfig();
            config.setMetaCentreAddr("dev.test:10241");
            MetaCentreBackendMetaManager metaManager = new MetaCentreBackendMetaManager(new MetricRegistry(), config);

            List<String> infohashs = new ArrayList(){{
//                add("050692A5029FC81CF7F75202167BED0C907F81C0");
//                add("939F6F30EE932B83DD7A41A7EA20CDFF8051BB23");
                add("050692A5029FC81CF7F75202167BED0C907F81C1");
                add("939F6F30EE932B83DD7A41A7EA20CDFF8051BB21");
            }};

//            for (String infohash : infohashs) {
//                byte[] meta = InputStream2ByteArray("/Users/caojianhua/Downloads/" + infohash);
//                metaManager.put(infohash, meta);
//                System.out.println(infohash + " puted");
//            }

            for (String infohash : infohashs) {
                System.out.println(infohash + ":" + metaManager.doesMetaExist(infohash));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] InputStream2ByteArray(String filePath) throws IOException {

        InputStream in = new FileInputStream(filePath);
        byte[] data = toByteArray(in);
        in.close();

        return data;
    }

    private static byte[] toByteArray(InputStream in) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 4];
        int n = 0;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
