package com.example.application.views;

import com.example.application.ModbusConnectionService;
import com.example.application.ModbusUnit;
import com.example.application.datalog.*;
import com.example.application.s7.*;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.PWA;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main Dashboard for RelacIT.
 * Simple data visualization and source management.
 */
@Route("")
@PWA(name = "RelacIT Dashboard", shortName = "RelacIT")
public class DashboardView extends VerticalLayout {

    private final ModbusConnectionService modbusService;
    private final S7ConnectionService s7Service;
    private final PlcPollingService modbusPolling;
    private final S7PollingService s7Polling;
    private final List<DataLogger> loggers;
    
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
            List<DataLogger> loggers) {
        this.modbusService = modbusService;
        this.s7Service = s7Service;
        this.modbusPolling = modbusPolling;
        this.s7Polling = s7Polling;
        this.loggers = loggers;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createLayout();
    }

    private void createLayout() {
        // Header
        H1 title = new H1("RelacIT - Industrial Gateway");
        title.getStyle()
            .set("font-size", "24px")
            .set("margin", "0")
            .set("color", "#1a365d");

        statusLabel.setText("● Ready");
        statusLabel.getStyle()
            .set("color", "#16a34a")
            .set("font-weight", "bold");

        HorizontalLayout header = new HorizontalLayout(title, statusLabel);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setWidthFull();

        // Data Sources Section
        H2 sourcesTitle = new H2("Data Sources");
        sourcesTitle.getStyle().set("font-size", "18px").set("margin-top", "20px");

        // Modbus Grid
        H2 modbusTitle = new H2("Modbus TCP");
        modbusTitle.getStyle().set("font-size", "16px").set("color", "#475569");

        modbusGrid.addColumn(ModbusUnit::getDisplayName)
            .setHeader("Name")
            .setSortable(true);
        modbusGrid.addColumn(u -> u.getHost() + ":" + u.getPort())
            .setHeader("Address");
        modbusGrid.addColumn(u -> u.getStatus().name())
            .setHeader("Status")
            .setSortable(true);
        modbusGrid.addColumn(new ComponentRenderer<>(unit -> {
            Button connectBtn = new Button(unit.getStatus() == ModbusUnit.Status.CONNECTED ? "Disconnect" : "Connect");
            connectBtn.addClickListener(e -> toggleModbusConnection(unit));
            
            Button configBtn = new Button(new Icon(VaadinIcon.COG), e -> showModbusConfig(unit));
            configBtn.getElement().setAttribute("title", "Configure Polling");
            
            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                modbusService.removeUnit(unit.getId());
                refreshModbusGrid();
            });
            deleteBtn.getElement().setAttribute("title", "Delete");
            deleteBtn.getStyle().set("color", "#dc2626");
            
            return new HorizontalLayout(connectBtn, configBtn, deleteBtn);
        })).setHeader("Actions");

        modbusGrid.setWidthFull();
        modbusGrid.setHeight("200px");

        Button addModbusBtn = new Button("+ Add Modbus Device", e -> showAddModbusDialog());
        addModbusBtn.setIcon(new Icon(VaadinIcon.PLUS));
        addModbusBtn.getStyle().set("background", "#2563eb").set("color", "white");

        // S7 Grid
        H2 s7Title = new H2("Siemens S7");
        s7Title.getStyle().set("font-size", "16px").set("color", "#475569").set("margin-top", "20px");

        s7Grid.addColumn(S7Unit::getDisplayName)
            .setHeader("Name")
            .setSortable(true);
        s7Grid.addColumn(u -> u.getHost() + " (R" + u.getRack() + "/S" + u.getSlot() + ")")
            .setHeader("Address");
        s7Grid.addColumn(u -> u.getStatus().name())
            .setHeader("Status")
            .setSortable(true);
        s7Grid.addColumn(new ComponentRenderer<>(unit -> {
            Button connectBtn = new Button(unit.getStatus() == S7Unit.Status.CONNECTED ? "Disconnect" : "Connect");
            connectBtn.addClickListener(e -> toggleS7Connection(unit));
            
            Button configBtn = new Button(new Icon(VaadinIcon.COG), e -> showS7Config(unit));
            configBtn.getElement().setAttribute("title", "Configure Polling");
            
            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                s7Service.removeUnit(unit.getId());
                refreshS7Grid();
            });
            deleteBtn.getElement().setAttribute("title", "Delete");
            deleteBtn.getStyle().set("color", "#dc2626");
            
            return new HorizontalLayout(connectBtn, configBtn, deleteBtn);
        })).setHeader("Actions");

        s7Grid.setWidthFull();
        s7Grid.setHeight("200px");

        Button addS7Btn = new Button("+ Add S7 Device", e -> showAddS7Dialog());
        addS7Btn.setIcon(new Icon(VaadinIcon.PLUS));
        addS7Btn.getStyle().set("background", "#2563eb").set("color", "white");

        // Logger Status
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

        if (loggers.isEmpty()) {
            loggersInfo.add(new Span("No loggers configured"));
        }

        // Stats
        dataStatsLabel.getStyle().set("font-family", "monospace").set("color", "#64748b");

        // Assemble
        add(header, sourcesTitle, modbusTitle, modbusGrid, addModbusBtn,
            s7Title, s7Grid, addS7Btn, loggersTitle, loggersInfo, dataStatsLabel);

        // Initial data
        refreshModbusGrid();
        refreshS7Grid();
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
        hostField.setPlaceholder("192.168.1.10");
        hostField.setValue("192.168.1.10");

        IntegerField portField = new IntegerField("Port");
        portField.setValue(502);

        IntegerField unitIdField = new IntegerField("Unit ID");
        unitIdField.setValue(1);

        Button addBtn = new Button("Add", e -> {
            ModbusUnit unit = modbusService.addUnit(
                hostField.getValue(),
                portField.getValue(),
                unitIdField.getValue()
            );
            refreshModbusGrid();
            dialog.close();
            Notification.show("Added " + unit.getDisplayName());
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(new VerticalLayout(hostField, portField, unitIdField, 
            new HorizontalLayout(addBtn, cancelBtn)));
        dialog.open();
    }

    private void showAddS7Dialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Siemens S7 Device");

        TextField hostField = new TextField("Host IP");
        hostField.setPlaceholder("192.168.1.20");
        hostField.setValue("192.168.1.20");

        IntegerField rackField = new IntegerField("Rack");
        rackField.setValue(0);

        IntegerField slotField = new IntegerField("Slot");
        slotField.setValue(1);

        Button addBtn = new Button("Add", e -> {
            S7Unit unit = s7Service.addUnit(
                hostField.getValue(),
                rackField.getValue(),
                slotField.getValue()
            );
            refreshS7Grid();
            dialog.close();
            Notification.show("Added " + unit.getDisplayName());
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(new VerticalLayout(hostField, rackField, slotField,
            new HorizontalLayout(addBtn, cancelBtn)));
        dialog.open();
    }

    private void showModbusConfig(ModbusUnit unit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure Polling: " + unit.getDisplayName());
        dialog.setWidth("400px");

        TextField registersField = new TextField("Registers (comma-separated)");
        registersField.setPlaceholder("0,1,2,3,4");

        IntegerField intervalField = new IntegerField("Poll Interval (ms)");
        intervalField.setValue(1000);

        Button startBtn = new Button("Start Polling", e -> {
            try {
                List<Integer> regs = Arrays.stream(registersField.getValue().split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .toList();
                
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

        dialog.add(new VerticalLayout(
            new Span("Configure which registers to poll:"),
            registersField,
            intervalField,
            new HorizontalLayout(startBtn, stopBtn, closeBtn)
        ));
        dialog.open();
    }

    private void showS7Config(S7Unit unit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure S7 Polling: " + unit.getDisplayName());
        dialog.setWidth("400px");

        ComboBox<String> areaCombo = new ComboBox<>("Area");
        areaCombo.setItems("DB", "M", "I", "Q");
        areaCombo.setValue("DB");

        IntegerField dbField = new IntegerField("DB Number");
        dbField.setValue(1);
        dbField.setHelperText("Only for DB area");

        IntegerField startField = new IntegerField("Start Byte");
        startField.setValue(0);

        IntegerField lengthField = new IntegerField("Length (bytes)");
        lengthField.setValue(10);

        IntegerField intervalField = new IntegerField("Poll Interval (ms)");
        intervalField.setValue(1000);

        Button startBtn = new Button("Start Polling", e -> {
            try {
                byte area = switch (areaCombo.getValue()) {
                    case "DB" -> S7Client.S7AREA_DB;
                    case "M" -> S7Client.S7AREA_MK;
                    case "I" -> S7Client.S7AREA_PE;
                    case "Q" -> S7Client.S7AREA_PA;
                    default -> S7Client.S7AREA_DB;
                };

                int dbNum = "DB".equals(areaCombo.getValue()) ? dbField.getValue() : 0;

                S7PollingService.DataAddress addr = new S7PollingService.DataAddress(
                    area, dbNum, startField.getValue(), lengthField.getValue()
                );

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

        dialog.add(new VerticalLayout(
            areaCombo, dbField, startField, lengthField, intervalField,
            new HorizontalLayout(startBtn, stopBtn, closeBtn)
        ));
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

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        ui = attachEvent.getUI();
        
        // Auto-refresh every 2 seconds
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
    protected void onDetach() {
        scheduler.shutdown();
    }
}
