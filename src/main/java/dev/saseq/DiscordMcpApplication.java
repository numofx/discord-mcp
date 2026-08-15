package dev.saseq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscordMcpApplication.class, args);
    }
}
