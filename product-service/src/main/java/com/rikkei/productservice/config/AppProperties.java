package com.rikkei.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue Cache cache, @DefaultValue Db db) {

    public record Cache(@DefaultValue("30m") Duration productTtl) {
    }

    public record Db(@DefaultValue("0ms") Duration simulatedLatency) {
    }
}
