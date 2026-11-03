package com.javachat.config;
import com.javachat.user.GoogleOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                               GoogleOidcUserService googleOidcUserService)
     throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/error").permitAll()
                .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService)))
                .csrf(csrf -> csrf.spa())    
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));

        return http.build();
    }

}