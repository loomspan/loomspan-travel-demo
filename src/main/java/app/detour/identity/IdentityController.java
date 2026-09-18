package app.detour.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import app.detour.api.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api")
public class IdentityController {
    private final IdentityService identityService;
    private final SecurityContextRepository securityContextRepository;
    private final boolean secureCookies;

    IdentityController(IdentityService identityService, SecurityContextRepository securityContextRepository,
            @Value("${detour.security.secure-cookies}") boolean secureCookies) {
        this.identityService = identityService;
        this.securityContextRepository = securityContextRepository;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/auth/register")
    ResponseEntity<ProfileResponse> register(@RequestBody IdentityRequests.Registration request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        DetourUserPrincipal principal = identityService.register(request);
        saveAuthentication(principal, servletRequest, servletResponse);
        return ResponseEntity.status(201).body(new ProfileResponse(principal.email()));
    }

    @PostMapping("/auth/login")
    ResponseEntity<Void> login(@RequestBody IdentityRequests.Login request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        saveAuthentication(identityService.authenticate(request), servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "")
                .path("/").httpOnly(true).secure(secureCookies).sameSite("Lax").maxAge(0).build().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    ProfileResponse profile(@AuthenticationPrincipal DetourUserPrincipal principal) {
        return identityService.profile(requirePrincipal(principal).userId());
    }

    @PutMapping("/profile/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal DetourUserPrincipal principal,
            @RequestBody IdentityRequests.PasswordChange request) {
        identityService.changePassword(requirePrincipal(principal).userId(), request);
        return ResponseEntity.noContent().build();
    }

    private void saveAuthentication(DetourUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = new SecurityContextImpl(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static DetourUserPrincipal requirePrincipal(DetourUserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Authentication is required.");
        }
        return principal;
    }
}
