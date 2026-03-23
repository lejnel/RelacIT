package com.example.application.views;

import com.example.application.ModbusConnectionService;
import com.example.application.ModbusUnit;
import com.example.application.datalog.*;
import com.example.application.s7.*;
import com.example.application.security.AdminAccountManager;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main Dashboard for RelacIT.
 * Requires login to access.
 */
@Route("")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final ModbusConnectionService modbusService;
    private final S7ConnectionService s7Service;
    private final PlcPollingService modbusPolling;
    private final S7PollingService s7Polling;
    private final List<DataLogger> loggers;
    private final AdminAccountManager adminManager;
    
    private final Grid<ModbusUnit> modbusGrid = new Grid<>();
    private final Grid<S7Unit> s7Grid = new Grid<>();
    private final Span statusLabel = new Span();
    private final Span dataStatsLabel = new Span();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private UI ui;

    @Autowired
    public DashboardView(
            ModbusConnectionService modbusService,
            S7ConnectionService s7Service,
            PlcPollingService modbusPolling,
            S7PollingService s7Polling,
            List<DataLogger> loggers,
            AdminAccountManager adminManager) {
        this.modbusService = modbusService;
        this.s7Service = s7Service;
        this.modbusPolling = modbusPolling;
        this.s7Polling = s7Polling;
        this.loggers = loggers;
        this.adminManager = adminManager;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (VaadinSession.getCurrent().getAttribute("user") == null) {
            event.forwardTo(LoginView.class);
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        ui = attachEvent.getUI();
        createLayout();
        
        scheduler.scheduleAtFixedRate(() -> {
            if (ui != null && !ui.isClosing()) {
                ui.access(() -> {
                    refreshModbusGrid();
                    refreshS7Grid();
                    updateStats();
                });
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        scheduler.shutdown();
    }

    private void createLayout() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("RelacIT - Industrial Gateway");
        title.getStyle().set("font-size", "24px").set("margin", "0").set("color", "#1a365d");

        statusLabel.setText("● Ready");
        statusLabel.getStyle().set("color", "#16a34a").set("font-weight", "bold");

        String username = (String) VaadinSession.getCurrent().getAttribute("user");
        Span userLabel = new Span("👤 " + username);
        userLabel.getStyle().set("color", "#64748b");

        Button exportBtn = new Button(new Icon(VaadinIcon.DOWNLOAD), e -> showExportDialog());
        exportBtn.getElement().setAttribute("title", "Export Configuration");
        
        Button importBtn = new Button(new Icon(VaadinIcon.UPLOAD), e -> showImportDialog());
        importBtn.getElement().setAttribute("title", "Import Configuration");

        Button logoutBtn = new Button("Logout", e -> {
            VaadinSession.getCurrent().close();
            UI.getCurrent().navigate(LoginView.class);
        });
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        logoutBtn.getStyle().set("color", "#dc2626");

        HorizontalLayout userActions = new HorizontalLayout(userLabel, exportBtn, importBtn, logoutBtn);
        userActions.setAlignItems(Alignment.CENTER);

        HorizontalLayout header = new HorizontalLayout(title, statusLabel, userActions);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setWidthFull();

        setupModbusGrid();
        setupS7Grid();

        Button addModbusBtn = new Button("+ Add Modbus Device", e -> showAddModbusDialog());
        addModbusBtn.setIcon(new Icon(VaadinIcon.PLUS));
        addModbusBtn.getStyle().set("background", "#2563eb").set("color", "white");

        Button addS7Btn = new Button("+ Add S7 Device", e -> showAddS7Dialog());
        addS7Btn.setIcon(new Icon(VaadinIcon.PLUS));
        addS7Btn.getStyle().set("background", "#2563eb").set("color", "white");

        H2 loggersTitle = new H2("Data Loggers");
        loggersTitle.getStyle().set("font-size", "16px").set("color", "#475569").set("margin-top", "20px");

        VerticalLayout loggersInfo = new VerticalLayout();
        loggersInfo.setSpacing(false);
        loggersInfo.setPadding(false);
        for (DataLogger logger : loggers) {
            String loggerName = logger.getClass().getSimpleName();
            String status = logger.getStatus().name();
            Span loggerLabel = new Span("● " + loggerName + ": " + status);
            loggerLabel.getStyle().set("color", 
                logger.getStatus() == DataLogger.LoggerStatus.LOGGING ? "#16a34a" : 
                logger.getStatus() == DataLogger.LoggerStatus.READY ? "#f59e0b" : "#94a3b8");
            loggersInfo.add(loggerLabel);
        }

        dataStatsLabel.getStyle().set("font-family", "monospace").set("color", "#64748b");

        add(header, loggersTitle, loggersInfo, modbusGrid, addModbusBtn, s7Grid, addS7Btn, dataStatsLabel);
        refreshModbusGrid();
        refreshS7Grid();
    }

    private void setupModbusGrid() {
        modbusGrid.removeAllColumns();
        modbusGrid.addColumn(u -> "Modbus@" + u.getHost() + ":" + u.getPort())
            .setHeader("Name").setSortable(true);
        modbusGrid.addColumn(u -> u.getHost() + ":" + u.getPort()).setHeader("Address");
        modbusGrid.addColumn(u -> u.getStatus().name()).setHeader("Status").setSortable(true);
        modbusGrid.addColumn(new ComponentRenderer<>(unit -> {
            Button connectBtn = new Button(unit.getStatus() == ModbusUnit.Status.CONNECTED ? "Disconnect" : "Connect");
            connectBtn.addClickListener(e -> toggleModbusConnection(unit));
            Button configBtn = new Button(new Icon(VaadinIcon.COG), e -> showModbusConfig(unit));
            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                modbusService.removeUnit(unit.getId());
                refreshModbusGrid();
            });
            deleteBtn.getStyle().set("color", "#dc2626");
            return new HorizontalLayout(connectBtn, configBtn, deleteBtn);
        })).setHeader("Actions");
        modbusGrid.setWidthFull();
        modbusGrid.setHeight("200px");
    }

    private void setupS7Grid() {
        s7Grid.removeAllColumns();
        s7Grid.addColumn(u -> "S7@" + u.getHost() + " (R" + u.getRack() + "/S" + u.getSlot() + ")")
            .setHeader("Name").setSortable(true);
        s7Grid.addColumn(u -> u.getHost() + " (R" + u.getRack() + "/S" + u.getSlot() + ")").setHeader("Address");
        s7Grid.addColumn(u -> u.getStatus().name()).setHeader("Status").setSortable(true);
        s7Grid.addColumn(new ComponentRenderer<>(unit -> {
            Button connectBtn = new Button(unit.getStatus() == S7Unit.Status.CONNECTED ? "Disconnect" : "Connect");
            connectBtn.addClickListener(e -> toggleS7Connection(unit));
            Button configBtn = new Button(new Icon(VaadinIcon.COG), e -> showS7Config(unit));
            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                s7Service.removeUnit(unit.getId());
                refreshS7Grid();
            });
            deleteBtn.getStyle().set("color", "#dc2626");
            return new HorizontalLayout(connectBtn, configBtn, deleteBtn);
        })).setHeader("Actions");
        s7Grid.setWidthFull();
        s7Grid.setHeight("200px");
    }

    private void showExportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("📤 Export Configuration");
        dialog.setWidth("500px");
        Paragraph info = new Paragraph("Copy this configuration to backup your admin account.");
        info.getStyle().set("color", "#64748b");
        try {
            String config = adminManager.exportConfig();
            TextArea configArea = new TextArea("Configuration");
            configArea.setValue(config);
            configArea.setWidthFull();
            configArea.setHeight("200px");
            configArea.setReadOnly(true);
            Button copyBtn = new Button("Copy to Clipboard", e -> {
                UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", config);
                Notification.show("Copied to clipboard!");
            });
            Button closeBtn = new Button("Close", e -> dialog.close());
            dialog.add(new VerticalLayout(info, configArea, new HorizontalLayout(copyBtn, closeBtn)));
        } catch (Exception e) {
            dialog.add(new Paragraph("Error: " + e.getMessage()));
        }
        dialog.open();
    }

    private void showImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("📥 Import Configuration");
        dialog.setWidth("500px");
        Paragraph warning = new Paragraph("⚠️ This will OVERWRITE the current admin account!");
        warning.getStyle().set("color", "#dc2626");
        TextArea configArea = new TextArea("Paste Configuration");
        configArea.setWidthFull();
        configArea.setHeight("200px");
        configArea.setPlaceholder("Paste your backup configuration here...");
        Button importBtn = new Button("Import", e -> {
            try {
                adminManager.importConfig(configArea.getValue());
                Notification.show("Configuration imported! Please login again.");
                VaadinSession.getCurrent().close();
                UI.getCurrent().navigate(LoginView.class);
                dialog.close();
            } catch (Exception ex) {
                Notification.show("Import failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        importBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(warning, configArea, new HorizontalLayout(importBtn, cancelBtn)));
        dialog.open();
    }

    private void toggleModbusConnection(ModbusUnit unit) {
        if (unit.getStatus() == ModbusUnit.Status.CONNECTED) {
            modbusService.disconnectAsync(unit);
            Notification.show("Disconnecting from " + unit.getHost());
        } else {
            modbusService.connectAsync(unit, 5000);
            Notification.show("Connecting to " + unit.getHost());
        }
        refreshModbusGrid();
    }

    private void toggleS7Connection(S7Unit unit) {
        if (unit.getStatus() == S7Unit.Status.CONNECTED) {
            s7Service.disconnectAsync(unit);
            Notification.show("Disconnecting from " + unit.getHost());
        } else {
            s7Service.connectAsync(unit, 5000);
            Notification.show("Connecting to " + unit.getHost());
        }
        refreshS7Grid();
    }

    private void showAddModbusDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Modbus Device");
        TextField hostField = new TextField("Host IP");
        hostField.setValue("192.168.1.10");
        IntegerField portField = new IntegerField("Port");
        portField.setValue(502);
        IntegerField unitIdField = new IntegerField("Unit ID");
        unitIdField.setValue(1);
        Button addBtn = new Button("Add", e -> {
            ModbusUnit unit = modbusService.addUnit(hostField.getValue(), portField.getValue(), unitIdField.getValue());
            refreshModbusGrid();
            dialog.close();
            Notification.show("Added Modbus@" + unit.getHost() + ":" + unit.getPort());
        });
        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(hostField, portField, unitIdField, new HorizontalLayout(addBtn, cancelBtn)));
        dialog.open();
    }

    private void showAddS7Dialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Siemens S7 Device");
        TextField hostField = new TextField("Host IP");
        hostField.setValue("192.168.1.20");
        IntegerField rackField = new IntegerField("Rack");
        rackField.setValue(0);
        IntegerField slotField = new IntegerField("Slot");
        slotField.setValue(1);
        Button addBtn = new Button("Add", e -> {
            S7Unit unit = s7Service.addUnit(hostField.getValue(), rackField.getValue(), slotField.getValue());
            refreshS7Grid();
            dialog.close();
            Notification.show("Added S7@" + unit.getHost());
        });
        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(hostField, rackField, slotField, new HorizontalLayout(addBtn, cancelBtn)));
        dialog.open();
    }

    private void showModbusConfig(ModbusUnit unit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure Polling: Modbus@" + unit.getHost());
        dialog.setWidth("400px");
        TextField registersField = new TextField("Registers (comma-separated)");
        registersField.setPlaceholder("0,1,2,3,4");
        IntegerField intervalField = new IntegerField("Poll Interval (ms)");
        intervalField.setValue(1000);
        Button startBtn = new Button("Start Polling", e -> {
            try {
                List<Integer> regs = Arrays.stream(registersField.getValue().split(","))
                    .map(String::trim).map(Integer::parseInt).toList();
                modbusPolling.configurePolling(unit.getId(), regs, intervalField.getValue());
                modbusPolling.startPolling(unit.getId());
                dialog.close();
                Notification.show("Polling started");
                updateStats();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage());
            }
        });
        Button stopBtn = new Button("Stop Polling", e -> {
            modbusPolling.stopPolling(unit.getId());
            dialog.close();
            Notification.show("Polling stopped");
        });
        Button closeBtn = new Button("Close", e -> dialog.close());
        dialog.add(new VerticalLayout(registersField, intervalField, new HorizontalLayout(startBtn, stopBtn, closeBtn)));
        dialog.open();
    }

    private void showS7Config(S7Unit unit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure S7 Polling: " + unit.getHost());
        dialog.setWidth("400px");
        ComboBox<String> areaCombo = new ComboBox<>("Area");
        areaCombo.setItems("DB", "M", "I", "Q");
        areaCombo.setValue("DB");
        IntegerField dbField = new IntegerField("DB Number");
        dbField.setValue(1);
        IntegerField startField = new IntegerField("Start Byte");
        startField.setValue(0);
        IntegerField lengthField = new IntegerField("Length (bytes)");
        lengthField.setValue(10);
        IntegerField intervalField = new IntegerField("Poll Interval (ms)");
        intervalField.setValue(1000);
        Button startBtn = new Button("Start Polling", e -> {
            try {
                byte area;
                if ("DB".equals(areaCombo.getValue())) area = S7Client.S7AREA_DB;
                else if ("M".equals(areaCombo.getValue())) area = S7Client.S7AREA_MK;
                else if ("I".equals(areaCombo.getValue())) area = S7Client.S7AREA_PE;
                else area = S7Client.S7AREA_PA;
                int dbNum = "DB".equals(areaCombo.getValue()) ? dbField.getValue() : 0;
                S7PollingService.DataAddress addr = new S7PollingService.DataAddress(area, dbNum, startField.getValue(), lengthField.getValue());
                s7Polling.configurePolling(unit.getId(), List.of(addr), intervalField.getValue());
                s7Polling.startPolling(unit.getId());
                dialog.close();
                Notification.show("S7 polling started");
                updateStats();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage());
            }
        });
        Button stopBtn = new Button("Stop Polling", e -> {
            s7Polling.stopPolling(unit.getId());
            dialog.close();
            Notification.show("Polling stopped");
        });
        Button closeBtn = new Button("Close", e -> dialog.close());
        dialog.add(new VerticalLayout(areaCombo, dbField, startField, lengthField, intervalField, new HorizontalLayout(startBtn, stopBtn, closeBtn)));
        dialog.open();
    }

    private void refreshModbusGrid() {
        modbusGrid.setItems(modbusService.listUnits());
    }

    private void refreshS7Grid() {
        s7Grid.setItems(s7Service.listUnits());
    }

    private void updateStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modbus Polling: ").append(modbusPolling.getPollingStatus()).append(" | ");
        sb.append("S7 Polling: ").append(s7Polling.getPollingStatus());
        dataStatsLabel.setText(sb.toString());
    }
}
