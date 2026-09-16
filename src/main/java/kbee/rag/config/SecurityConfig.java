package kbee.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JSON API protected with HTTP Basic.
 *
 * Credentials are configured in application.yml
 * (spring.security.user.name / password); clients must send
 * an Authorization: Basic header on every request.
 *
 * CSRF is disabled (no browser session/cookies involved) and
 * no HTTP session is created.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS
                    )
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/health"
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
