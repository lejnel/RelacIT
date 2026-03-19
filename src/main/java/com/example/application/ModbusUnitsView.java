package com.example.application;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;

@Route("modbus-units")
public class ModbusUnitsView extends VerticalLayout {

    private final ModbusConnectionService service;
    private final Grid<ModbusUnit> grid = new Grid<>(ModbusUnit.class, false);

    @Autowired
    public ModbusUnitsView(ModbusConnectionService service) {
        this.service = service;
        setSizeFull();

        grid.addColumn(ModbusUnit::getHost).setHeader("Host").setAutoWidth(true);
        grid.addColumn(ModbusUnit::getPort).setHeader("Port");
        grid.addColumn(ModbusUnit::getUnitId).setHeader("Unit");
        grid.addColumn(u -> u.getStatus().name()).setHeader("Status");
        grid.addColumn(u -> {
            if (u.getLastUpdated() == null) return "";
            return DateTimeFormatter.ISO_INSTANT.format(u.getLastUpdated());
        }).setHeader("Last updated");

        grid.addComponentColumn(u -> actionLayout(u)).setHeader("Actions");

        Button refresh = new Button("Refresh", e -> refreshGrid());

        add(new Span("Managed Modbus connections"), refresh, grid);

        // poll UI to reflect status changes
        UI.getCurrent().setPollInterval(2000);
        UI.getCurrent().addPollListener(ev -> refreshGrid());

        refreshGrid();
    }

    private HorizontalLayout actionLayout(ModbusUnit u) {
        Button connect = new Button("Connect", e -> {
            u.setStatus(ModbusUnit.Status.CONNECTING, "Connecting");
            service.connectAsync(u, 5000);
            refreshGrid();
        });
        Button disconnect = new Button("Disconnect", e -> {
            service.disconnectAsync(u);
            refreshGrid();
        });
        Button remove = new Button("Remove", e -> {
            service.removeUnit(u.getId());
            refreshGrid();
        });
        HorizontalLayout hl = new HorizontalLayout(connect, disconnect, remove);
        return hl;
    }

    private void refreshGrid() {
        grid.setItems(service.listUnits());
    }
}
