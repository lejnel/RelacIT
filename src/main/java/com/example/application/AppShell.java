package com.example.application;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import org.springframework.stereotype.Component;

/**
 * App shell configuration for PWA support.
 */
@Component
@PWA(name = "RelacIT Dashboard", shortName = "RelacIT")
public class AppShell implements AppShellConfigurator {
}
