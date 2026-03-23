package com.example.application.views;

import com.example.application.security.AdminAccountManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * First-time setup page for creating admin account.
 */
@Route("setup")
public class SetupView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminAccountManager adminManager;

    @Autowired
    public SetupView(AdminAccountManager adminManager) {
        this.adminManager = adminManager;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1 title = new H1("🏭 RelacIT Setup");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Create your admin account to get started");
        subtitle.getStyle().set("color", "#64748b").set("margin-bottom", "2rem");

        Paragraph warning = new Paragraph("⚠️ IMPORTANT: This password cannot be recovered!");
        warning.getStyle()
            .set("color", "#dc2626")
            .set("font-weight", "bold")
            .set("background", "#fef2f2")
            .set("padding", "1rem")
            .set("border-radius", "8px")
            .set("margin-bottom", "1rem");

        Paragraph info = new Paragraph(
            "If you forget your password, you'll need to reinstall. " +
            "Export your configuration regularly for backup."
        );
        info.getStyle().set("color", "#64748b").set("font-size", "0.875rem").set("margin-bottom", "2rem");

        TextField usernameField = new TextField("Username");
        usernameField.setPlaceholder("admin");
        usernameField.setRequired(true);
        usernameField.setMinLength(3);
        usernameField.setWidth("300px");

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setPlaceholder("Min 8 characters");
        passwordField.setRequired(true);
        passwordField.setMinLength(8);
        passwordField.setWidth("300px");

        PasswordField confirmPasswordField = new PasswordField("Confirm Password");
        confirmPasswordField.setPlaceholder("Repeat password");
        confirmPasswordField.setRequired(true);
        confirmPasswordField.setWidth("300px");

        Button createButton = new Button("Create Admin Account", e -> {
            String username = usernameField.getValue();
            String password = passwordField.getValue();
            String confirm = confirmPasswordField.getValue();

            if (username == null || username.trim().length() < 3) {
                Notification.show("Username must be at least 3 characters", 3000, Notification.Position.MIDDLE);
                return;
            }
            if (password == null || password.length() < 8) {
                Notification.show("Password must be at least 8 characters", 3000, Notification.Position.MIDDLE);
                return;
            }
            if (!password.equals(confirm)) {
                Notification.show("Passwords do not match", 3000, Notification.Position.MIDDLE);
                return;
            }

            try {
                adminManager.createAdminAccount(username, password);
                VaadinSession.getCurrent().setAttribute("user", username);
                Notification.show("Account created! Redirecting...", 3000, Notification.Position.MIDDLE);
                UI.getCurrent().navigate("");
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.setWidth("300px");
        createButton.getStyle().set("margin-top", "1rem");

        add(title, subtitle, warning, info, usernameField, passwordField, confirmPasswordField, createButton);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // If admin already exists, redirect to login
        boolean adminExists = adminManager.isAdminCreated();
        System.out.println("SetupView.beforeEnter - adminExists: " + adminExists);
        if (adminExists) {
            System.out.println("Redirecting to login because admin exists");
            event.forwardTo(LoginView.class);
        }
    }
}
