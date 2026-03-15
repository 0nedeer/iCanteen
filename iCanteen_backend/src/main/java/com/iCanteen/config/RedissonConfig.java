package com.iCanteen.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RedissonConfig {

    private static final long LOCK_WATCHDOG_TIMEOUT_MS = 30000L;
    @Resource
    private RedisProperties redisProperties;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Default lease with watchdog auto-renew to avoid lock timeout under long tasks.
        config.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT_MS);

        if (redisProperties.getCluster() != null
                && redisProperties.getCluster().getNodes() != null
                && !redisProperties.getCluster().getNodes().isEmpty()) {
            ClusterServersConfig clusterServersConfig = config.useClusterServers()
                    .addNodeAddress(toRedisAddresses(redisProperties.getCluster().getNodes()))
                    .setPassword(emptyToNull(redisProperties.getPassword()));
            if (redisProperties.getTimeout() != null) {
                clusterServersConfig.setTimeout((int) redisProperties.getTimeout().toMillis());
            }
            return Redisson.create(config);
        }

        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(normalizeRedisAddress(redisProperties.getHost() + ":" + redisProperties.getPort()))
                .setDatabase(redisProperties.getDatabase())
                .setPassword(emptyToNull(redisProperties.getPassword()));
        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout((int) redisProperties.getTimeout().toMillis());
        }
        return Redisson.create(config);
    }

    private String[] toRedisAddresses(List<String> nodes) {
        return nodes.stream().map(this::normalizeRedisAddress).collect(Collectors.toList()).toArray(new String[0]);
    }

    private String normalizeRedisAddress(String address) {
        if (address.startsWith("redis://") || address.startsWith("rediss://")) {
            return address;
        }
        return "redis://" + address;
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }
}
