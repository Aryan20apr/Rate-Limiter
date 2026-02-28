package com.ratelimiter.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ratelimiter.core.config.RateLimited;


@RestController
public class DemoController {

    @GetMapping("/test")
    public String test() {
        return "OK";
    }

    @RateLimited
    @GetMapping("/aop-test")
    public String aopTest() {
        return "AOP OK";
    }
}
