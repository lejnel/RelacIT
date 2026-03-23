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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Login page for RelacIT.
 */
@Route("login")
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminAccountManager adminManager;

    @Autowired
    public LoginView(AdminAccountManager adminManager) {
        this.adminManager = adminManager;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

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

            if (adminManager.validateLogin(username, password)) {
                VaadinSession.getCurrent().setAttribute("user", username);
                UI.getCurrent().navigate("");
            } else {
                Notification.show("Invalid username or password", 3000, Notification.Position.MIDDLE);
            }
        });
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidth("300px");

        Button setupLink = new Button("First time? Create admin account", e -> {
            UI.getCurrent().navigate(SetupView.class);
        });
        setupLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        add(title, subtitle, usernameField, passwordField, loginButton, setupLink);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // If no admin exists, redirect to setup
        if (!adminManager.isAdminCreated()) {
            event.forwardTo(SetupView.class);
        }
        // If already logged in, go to dashboard
        if (VaadinSession.getCurrent().getAttribute("user") != null) {
            event.forwardTo("");
        }
    }
}
