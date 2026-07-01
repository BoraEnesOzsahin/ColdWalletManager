package com.ayrotek.coldwalletmanagerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class ColdWalletManagerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColdWalletManagerServiceApplication.class, args);
    }

}
