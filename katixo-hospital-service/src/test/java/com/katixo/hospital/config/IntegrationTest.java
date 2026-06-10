package com.katixo.hospital.config;

import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("katixo_hospital_test")
            .withUsername("katixo")
            .withPassword("test_password");

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    protected static final String TEST_TENANT_ID = "test-tenant-001";
    protected static final String TEST_GROUP_ID = "1";
    protected static final String TEST_BRANCH_ID = "1";
    protected static final String TEST_USER_ID = "user-001";

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @BeforeEach
    void setUp() {
        // Initialize tenant context for each test
        TenantContext context = new TenantContext(
                TEST_TENANT_ID,
                TEST_GROUP_ID,
                TEST_BRANCH_ID,
                TEST_USER_ID,
                "test-user"
        );
        TenantContext.set(context);
    }

    protected void clearTenantContext() {
        TenantContext.clear();
    }
}
