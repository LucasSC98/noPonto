package me.lucascustodio.noponto.config;

import me.lucascustodio.noponto.tenant.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final long CORS_MAX_AGE_SEGUNDOS = 3600L;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        // Preflight do browser, antes de qualquer JWT.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Login da clínica. Não há sessão; o token sai na resposta.
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        // Agenda pública: profissionais, serviços e slots. Só leitura.
                        .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                        // Cliente cria o agendamento sem conta. Outros POST em /public/** seguem autenticados.
                        .requestMatchers(HttpMethod.POST, "/public/agendamentos").permitAll()
                        // Cliente cancela pelo token do agendamento, sem JWT de admin.
                        .requestMatchers(HttpMethod.POST, "/public/appointments/manage/{token}/cancel").permitAll()
                        // Cliente remarca pelo mesmo token. Não abre POST para o restante de /public/**.
                        .requestMatchers(HttpMethod.POST, "/public/appointments/manage/{token}/reschedule").permitAll()
                        // Corpo de erro do Spring. Sem isso, um 401 vira outro 401 no /error.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${noponto.cors.origem-permitida}") String origemPermitida) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(List.of(origemPermitida));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Subdominio"));
        configuracao.setExposedHeaders(List.of("Authorization"));
        configuracao.setAllowCredentials(true);
        configuracao.setMaxAge(CORS_MAX_AGE_SEGUNDOS);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> desabilitarRegistroAutomaticoJwtAuthFilter(
            JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registro = new FilterRegistrationBean<>(jwtAuthFilter);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
