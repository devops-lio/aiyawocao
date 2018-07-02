package com.killxdcj.aiyawocao.meta.centre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MetaCentreMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaCentreMain.class);

    public static void main(String[] args) {
        SpringApplication.run(MetaCentreMain.class, args);
    }
}
