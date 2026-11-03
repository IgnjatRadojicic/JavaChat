package com.javachat.account;

import com.javachat.user.User;
import com.javachat.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final AccountService accountService;
    private final UserService userService;

    public AccountController(AccountService accountService, UserService userService) {
        this.accountService = accountService;
        this.userService = userService;
    }

    @DeleteMapping("/api/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal OidcUser principal,
                                              HttpServletRequest request) throws ServletException {
        User user = userService.getByGoogleSub(principal.getSubject());
        accountService.deleteAccount(user.getId());
        request.logout();
        return ResponseEntity.noContent().build();
    }
}
