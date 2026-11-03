package com.javachat.memory;

import com.javachat.user.User;
import com.javachat.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;
    private final UserService userService;

    public MemoryController(MemoryService memoryService, UserService userService) {
        this.memoryService = memoryService;
        this.userService = userService;
    }

    @GetMapping
    public List<MemoryResponse> list(@AuthenticationPrincipal OidcUser principal) {
        User user = userService.getByGoogleSub(principal.getSubject());
        return memoryService.listMemories(user.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser principal, @PathVariable UUID id) {
        User user = userService.getByGoogleSub(principal.getSubject());
        return memoryService.forget(user.getId(), id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
