package com.example.application.views;

import com.example.application.security.AdminAccountManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Login page for RelacIT.
 * Redirects to setup if no admin exists.
 */
@Route("login")
public class LoginView extends VerticalLayout {

    @Autowired
    public LoginView(AdminAccountManager adminManager) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // If no admin, redirect to setup (deferred to avoid UI issues)
        if (!adminManager.isAdminCreated()) {
            UI.getCurrent().accessLater(() -> {
                UI.getCurrent().navigate(SetupView.class);
            }, null);
            return;
        }

        H1 title = new H1("🏭 RelacIT");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Industrial Gateway");
        subtitle.getStyle().set("color", "#64748b").set("margin-bottom", "2rem");

        TextField usernameField = new TextField("Username");
        usernameField.setPlaceholder("Enter username");
        usernameField.setRequired(true);
        usernameField.setWidth("300px");
        usernameField.focus();

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setPlaceholder("Enter password");
        passwordField.setRequired(true);
        passwordField.setWidth("300px");

        Button loginButton = new Button("Login", e -> {
            String username = usernameField.getValue();
            String password = passwordField.getValue();

            if (username.isEmpty() || password.isEmpty()) {
                Notification.show("Please enter both username and password", 3000, Notification.Position.MIDDLE);
                return;
            }

            if (adminManager.validateLogin(username, password)) {
                // Store login state in session
                VaadinSession.getCurrent().setAttribute("user", username);
                Notification.show("Welcome, " + username + "!", 2000, Notification.Position.MIDDLE);
                UI.getCurrent().navigate(DashboardView.class);
            } else {
                Notification.show("Invalid username or password", 3000, Notification.Position.MIDDLE);
                passwordField.clear();
                passwordField.focus();
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidth("300px");
        loginButton.getStyle().set("margin-top", "1rem");

        // Allow Enter key to submit
        passwordField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> loginButton.click());

        Paragraph configInfo = new Paragraph(
            "Config location: " + AdminAccountManager.getConfigLocation()
        );
        configInfo.getStyle().set("color", "#94a3b8").set("font-size", "0.75rem").set("margin-top", "2rem");

        add(title, subtitle, usernameField, passwordField, loginButton, configInfo);
    }
}
