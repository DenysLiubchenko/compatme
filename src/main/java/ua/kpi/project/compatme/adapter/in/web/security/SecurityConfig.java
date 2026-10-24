package ua.kpi.project.compatme.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ua.kpi.project.compatme.config.TelegramBotProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Stateless API security: every {@code /api/**} call must carry either a user JWT
 * ({@code Authorization: Bearer ...}, obtained from {@code /api/v1/auth/telegram}) or the trusted
 * service key ({@code X-Service-Key}). Per-profile ownership is enforced separately by
 * {@link ProfileAccessPolicy} through method security on the controllers.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, TelegramBotProperties.class})
public class SecurityConfig {

    @Bean
    public Clock securityClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, SecurityProperties properties, JwtDecoder jwtDecoder, ObjectMapper objectMapper)
            throws Exception {
        var entryPoint = JsonSecurityErrorHandlers.authenticationEntryPoint(objectMapper);
        var deniedHandler = JsonSecurityErrorHandlers.accessDeniedHandler(objectMapper);

        http
                .csrf(AbstractHttpConfigurer::disable) // stateless, header-based credentials only (no cookies)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/telegram").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterBefore(
                        new ServiceKeyAuthenticationFilter(properties.getServiceKey(), entryPoint),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.getAllowedOrigins().stream()
                .map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cors);
        }
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtTokenService.ISSUER));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    @Bean
    public JwtTokenService jwtTokenService(JwtEncoder encoder, SecurityProperties properties, Clock securityClock) {
        return new JwtTokenService(encoder, properties.getJwtTtl(), securityClock);
    }

    @Bean
    public TelegramInitDataValidator telegramInitDataValidator(
            TelegramBotProperties telegram, SecurityProperties properties, Clock securityClock) {
        return new TelegramInitDataValidator(telegram.getBotToken(), properties.getInitDataMaxAge(), securityClock);
    }

    private static SecretKey signingKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
