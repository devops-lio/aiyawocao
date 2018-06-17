package com.killxdcj.aiyawocao;

import com.killxdcj.aiyawocao.meta.manager.AliOSSBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) {
        System.out.println( "Hello World!" );
        App.testOSS();

    }

    public static void testOSS() {
        try {
            AliOSSBackendMetaManager metacenter = new AliOSSBackendMetaManager(new MetaManagerConfig());

            System.out.println(metacenter.doesMetaExist("e433b1b341df7f9559810de8fcfa8ada9bca7415"));
            System.out.println(metacenter.doesMetaExist("e433b1b341df7f9559810de8fcfa8ada9bca7416"));
            System.out.println(metacenter.doesMetaExist("e433b1b341df7f9559810de8fcfa8ada9bca7418"));
            metacenter.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
