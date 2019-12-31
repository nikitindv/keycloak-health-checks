package com.github.thomasdarimont.keycloak.healthchecker.spi.infinispan;

import com.github.thomasdarimont.keycloak.healthchecker.model.HealthStatus;
import com.github.thomasdarimont.keycloak.healthchecker.model.KeycloakHealthStatus;
import com.github.thomasdarimont.keycloak.healthchecker.spi.AbstractHealthIndicator;
import org.infinispan.health.ClusterHealth;
import org.infinispan.health.Health;
import org.infinispan.manager.EmbeddedCacheManager;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

import javax.naming.InitialContext;
import javax.naming.NamingException;


public class InfinispanHealthIndicator extends AbstractHealthIndicator {

    private final KeycloakSession session;

    private static final String KEYCLOAK_CACHE_MANAGER_JNDI_NAME = "java:jboss/infinispan/container/keycloak";

    InfinispanHealthIndicator(KeycloakSession session, Config.Scope config) {
        super("infinispan");
        this.session = session;
    }

    @Override
    public HealthStatus check() {

        try {

            String host = this.session.getContext().getUri().getRequestUri().getHost();
            String hostHealthKey = "__health_check" + host;
            Health infinispanHealth = lookupCacheManager().getHealth();
            ClusterHealth clusterHealth = infinispanHealth.getClusterHealth();

            KeycloakHealthStatus status = determineClusterHealth(clusterHealth);

            status//
                    .withAttribute("clusterName", clusterHealth.getClusterName()) //
                    .withAttribute("healthStatus", clusterHealth.getHealthStatus()) //
                    .withAttribute("numberOfNodes", clusterHealth.getNumberOfNodes())
                    .withAttribute("host", host);

            lookupCacheManager().getCache("work").put(hostHealthKey, 1);
            lookupCacheManager().getCache("work").get(hostHealthKey);

            return status;
        } catch (Exception ex) {
            return reportDown();
        }
    }

    private EmbeddedCacheManager lookupCacheManager() {
        try {
            return (EmbeddedCacheManager) new InitialContext().lookup(KEYCLOAK_CACHE_MANAGER_JNDI_NAME);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private KeycloakHealthStatus determineClusterHealth(ClusterHealth clusterHealth) {

        if (clusterHealth.getHealthStatus() == org.infinispan.health.HealthStatus.HEALTHY) {
            return reportUp();
        }
        return reportDown();
    }
}
