package com.javachat.chat;

import com.javachat.user.User;
import com.javachat.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;

    public ChatController(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
    }

    @PostMapping
    public ChatMessageResponse chat(@AuthenticationPrincipal OidcUser principal,
                                    @Valid @RequestBody ChatRequest request) {
        User user = userService.getByGoogleSub(principal.getSubject());
        ChatMessage reply = chatService.reply(user, request.message());
        return ChatMessageResponse.from(reply);
    }

    @GetMapping("/history")
    public List<ChatMessageResponse> history(@AuthenticationPrincipal OidcUser principal) {
        User user = userService.getByGoogleSub(principal.getSubject());
        return chatService.getHistory(user.getId()).stream()
            .map(ChatMessageResponse::from)
            .toList();
    }
}