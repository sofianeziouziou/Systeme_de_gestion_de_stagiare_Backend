package com.hikma.stagiaires;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableMongoAuditing       // Active @CreatedDate / @LastModifiedDate sur les entités
@EnableScheduling          // Active les @Scheduled (notifications automatiques)
@EnableAsync               // Active les @Async (AuditLogService)
@EnableCaching             // Active le cache Redis (@Cacheable sur DashboardService)
public class SimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimsApplication.class, args);
    }
}