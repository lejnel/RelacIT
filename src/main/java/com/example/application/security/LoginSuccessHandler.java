package com.example.application.security;

import org.springframework.stereotype.Component;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.component.UI;

/**
 * Handles successful login by redirecting to dashboard.
 * Simplified - no Spring Security dependencies.
 */
@Component
public class LoginSuccessHandler {

    public void onLoginSuccess(String username) {
        VaadinSession.getCurrent().setAttribute("user", username);
        UI.getCurrent().navigate("");
    }
}
