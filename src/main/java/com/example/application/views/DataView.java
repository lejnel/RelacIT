package com.example.application.views;

import com.example.application.ModbusConnectionService;
import com.example.application.ModbusUnit;
import com.example.application.datalog.*;
import com.example.application.s7.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import java.util.List;

/**
 * Simple data view showing live values from PLCs.
 */
@Route("data")
@RouteAlias(value = "", layout = DashboardView.class)
public class DataView extends VerticalLayout {

    private final Grid<DataRow> dataGrid = new Grid<>();

    public DataView(
            ModbusConnectionService modbusService,
            S7ConnectionService s7Service,
            PlcPollingService modbusPolling,
            S7PollingService s7Polling) {

        setSizeFull();
        setPadding(true);

        H1 title = new H1("📊 Live Data");
        title.getStyle().set("font-size", "24px");

        dataGrid.addColumn(DataRow::getUnit).setHeader("Unit");
        dataGrid.addColumn(DataRow::getAddress).setHeader("Address");
        dataGrid.addColumn(DataRow::getValue).setHeader("Value");
        dataGrid.addColumn(DataRow::getTimestamp).setHeader("Last Update");
        dataGrid.setWidthFull();

        Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH), e -> refreshData());

        add(title, refreshBtn, dataGrid);

        // Sample data for demo
        dataGrid.setItems(List.of(
            new DataRow("Modbus-1", "Register 0", "1234", "2026-03-22 22:20:01"),
            new DataRow("Modbus-1", "Register 1", "5678", "2026-03-22 22:20:01"),
            new DataRow("S7-1", "DB1.DBW0", "100", "2026-03-22 22:20:00"),
            new DataRow("S7-1", "DB1.DBW2", "200", "2026-03-22 22:20:00")
        ));
    }

    private void refreshData() {
        Notification.show("Data refreshed");
    }

    public static class DataRow {
        private final String unit;
        private final String address;
        private final String value;
        private final String timestamp;

        public DataRow(String unit, String address, String value, String timestamp) {
            this.unit = unit;
            this.address = address;
            this.value = value;
            this.timestamp = timestamp;
        }

        public String getUnit() { return unit; }
        public String getAddress() { return address; }
        public String getValue() { return value; }
        public String getTimestamp() { return timestamp; }
    }
}
