package com.ayrotek.coldwalletmanagerservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.lang.NonNull;

@Configuration
public class VaultConfig extends AbstractVaultConfiguration {

    @Override
    @NonNull
    public VaultEndpoint vaultEndpoint() {
        // This will be read from application.properties: spring.cloud.vault.uri
        String vaultUri = getEnvironment().getRequiredProperty("spring.cloud.vault.uri");
        return VaultEndpoint.from(java.net.URI.create(vaultUri));
    }

    @Override
    @NonNull
    public ClientAuthentication clientAuthentication() {
        // Read AppRole credentials from application.properties
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(
                        getEnvironment().getRequiredProperty("spring.cloud.vault.app-role.role-id")
                ))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(
                        getEnvironment().getRequiredProperty("spring.cloud.vault.app-role.secret-id")
                ))
                .build();

        return new AppRoleAuthentication(options, restOperations());
    }
}
