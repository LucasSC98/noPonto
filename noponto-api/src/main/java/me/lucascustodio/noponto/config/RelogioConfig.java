package me.lucascustodio.noponto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RelogioConfig {

    /** Relógio único da aplicação, em UTC. Injetável para permitir testes com horário fixo. */
    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
