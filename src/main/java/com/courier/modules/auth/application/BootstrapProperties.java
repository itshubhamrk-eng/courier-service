package com.courier.modules.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code app.bootstrap.*}: one-time first-start settings. Leave unset in normal operation. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {

    private String superAdminEmail;
    private String superAdminPassword;
    private String superAdminFirstName = "Platform";
    private String superAdminLastName = "Admin";
}
