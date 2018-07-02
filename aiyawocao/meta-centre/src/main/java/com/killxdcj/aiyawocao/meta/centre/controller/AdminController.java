package com.killxdcj.aiyawocao.meta.centre.controller;

import com.killxdcj.aiyawocao.meta.centre.wrapper.AliOSSBAckendMetaCentreWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
@RequestMapping("admin")
public class AdminController {

    @Autowired
    private AliOSSBAckendMetaCentreWrapper aliOSSBAckendMetaCentreWrapper;

    @RequestMapping("shutdown")
    public Object shutdown() {
        aliOSSBAckendMetaCentreWrapper.shutdown();
        return new HashMap<String, Object>() {{
            put("errno", 0);
        }};
    }
}
