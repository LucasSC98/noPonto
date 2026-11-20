package me.lucascustodio.noponto.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DevDataSeederPerfilTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void deve_naoExistirBean_quando_perfilDevInativo() {
        assertThat(contexto.getEnvironment().getActiveProfiles()).doesNotContain("dev");
        assertThat(contexto.getBeansOfType(DevDataSeeder.class)).isEmpty();
    }
}
