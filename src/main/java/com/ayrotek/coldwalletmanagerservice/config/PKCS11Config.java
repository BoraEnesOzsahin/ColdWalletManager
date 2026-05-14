package com.ayrotek.coldwalletmanagerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Provider;
import java.security.Security;

@Configuration
public class PKCS11Config {

    @Value("${pkcs11.library.path}")
    private String pkcs11LibraryPath;

    @Value("${pkcs11.slot-list-index:0}")
    private int slotListIndex;

    @Bean
    public Provider pkcs11Provider() {
        String pkcs11Config = "--" +
                "name = SoftHSMProxy\n" +
                "library = " + pkcs11LibraryPath + "\n" +
                "slotListIndex = " + slotListIndex + "\n" +
                "attributes = compatibility\n" +
                "showInfo = true\n";

        Provider baseProvider = Security.getProvider("SunPKCS11");

        if (baseProvider == null) {
            throw new IllegalStateException("SunPKCS11 provider is not available in the current JVM.");
        }

        Provider configuredProvider = baseProvider.configure(pkcs11Config);
        Security.addProvider(configuredProvider);

        return configuredProvider;
    }
}