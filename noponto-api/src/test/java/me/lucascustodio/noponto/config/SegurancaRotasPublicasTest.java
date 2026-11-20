package me.lucascustodio.noponto.config;

import me.lucascustodio.noponto.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class SegurancaRotasPublicasTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deve_retornar401_quando_postSemJwtEmRotaPublicaNaoLiberada() throws Exception {
        mockMvc.perform(post("/public/outra-rota"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deve_retornar401_quando_rotaAutenticadaSemJwt() throws Exception {
        mockMvc.perform(get("/agendamentos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/profissionais"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deve_liberarPreflight_quando_options() throws Exception {
        mockMvc.perform(options("/agendamentos")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void deve_naoExigirJwt_quando_cancelarOuRemarcarPeloTokenPublico() throws Exception {
        mockMvc.perform(post("/public/appointments/manage/token-do-cliente/cancel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/public/appointments/manage/token-do-cliente/reschedule"))
                .andExpect(status().isNotFound());
    }
}
