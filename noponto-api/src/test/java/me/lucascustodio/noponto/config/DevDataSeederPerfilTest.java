package me.lucascustodio.noponto.config;

import me.lucascustodio.noponto.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class DevDataSeederPerfilTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void deve_naoExistirBean_quando_perfilDevInativo() {
        assertThat(contexto.getEnvironment().getActiveProfiles()).doesNotContain("dev");
        assertThat(contexto.getBeansOfType(DevDataSeeder.class)).isEmpty();
    }
}
