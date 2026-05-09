package com.ayrotek.coldwalletmanagerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Provider;
import java.security.Security;

@Configuration
public class PKCS11Config {

    // Inject the library path from application.properties or environment variables.
    // Provide a default value for convenience in our current Docker setup.
    @Value("${pkcs11.library.path}")
    private String pkcs11LibraryPath;

    @Bean
    public Provider pkcs11Provider() {
        // In Java 9 and newer, direct instantiation of sun.security.pkcs11.SunPKCS11 is blocked by the module system.
        // The modern standard is to retrieve the provider from Security and use .configure() with an inline configuration string starting with "--".
        String pkcs11Config = "--name=SoftHSM\n" +
                "library=" + pkcs11LibraryPath + "\n" +
                "slotListIndex=0\n" +
                "attributes=compatibility\n" +
                "showInfo=true\n";

        Provider baseProvider = Security.getProvider("SunPKCS11");
        if (baseProvider == null) {
            throw new IllegalStateException("SunPKCS11 provider is not available in the current JVM.");
        }

        // Configure the provider instance
        Provider pkcs11Provider = baseProvider.configure(pkcs11Config);
        Security.addProvider(pkcs11Provider);

        return pkcs11Provider;
    }
}
