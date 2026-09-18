package app.detour.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.HttpMethod;

@Configuration
class SecurityConfiguration {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Prevent Boot's development-only generated user from becoming an accidental credential. */
    @Bean
    UserDetailsService noDefaultUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No default account is configured.");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
            JsonAuthenticationEntryPoint authenticationEntryPoint, JsonAccessDeniedHandler accessDeniedHandler,
            @Value("${detour.security.secure-cookies}") boolean secureCookies) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository(secureCookies))
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new SpaCsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository).requireExplicitSave(true))
                .sessionManagement(session -> session.sessionFixation(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/demo-disclosure/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private CookieCsrfTokenRepository csrfTokenRepository(boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookies));
        return repository;
    }
}
