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
        for (int i = 0; i < 100; i++) {
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
            MetaCentreBackendMetaManager metaManager = new MetaCentreBackendMetaManager(new MetricRegistry(), new MetaManagerConfig());

            List<String> infohashs = new ArrayList(){{
                add("0C06935610A702129AAA186741B96E91C6E200A9");
                add("00000883D6E40593F6ABADE71D74C5E8A44AD582");
                add("06009B8675D3ECDAD5BFF434CE8DDDC180D431BC");
                add("df81104ac5d84223f92ff0f61233d407350b19b5");
            }};

            for (String infohash : infohashs) {
                byte[] meta = InputStream2ByteArray("/home/user/Downloads/" + infohash);
                metaManager.put(infohash, meta);
                System.out.println(infohash + " puted");
            }

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
