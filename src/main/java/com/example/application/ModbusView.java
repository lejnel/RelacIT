package com.example.application;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Vaadin page for configuring Modbus TCP connections and viewing received data.
 */
@Route("modbus")
public class ModbusView extends VerticalLayout {

    private final TextField host = new TextField("Host, 192.168.0.130");
    private final TextField port = new TextField("Port", "5020");
    private final TextField unitId = new TextField("Unit ID", "1");
    private final TextField start = new TextField("Start", "0");
    private final TextField count = new TextField("Count", "1");

    private final TextField writeAddress = new TextField("Write Address", "0");
    private final TextField writeValue = new TextField("Write Value", "0");

    private final Button connect = new Button("Connect");
    private final Button disconnect = new Button("Disconnect");
    private final Button read = new Button("Read");
    private final Button write = new Button("Write");
    private final Button manage = new Button("Manage connection");

    private final TextArea console = new TextArea("Console");

    // connection UI
    private final Span status = new Span("Not connected");
    private final ProgressBar progress = new ProgressBar();

    private volatile ModbusTcpClient client;
    private volatile boolean connecting = false;
    private final int defaultTimeoutMs = 5000;

    private final ModbusConnectionService connectionService;

    @Autowired
    public ModbusView(ModbusConnectionService connectionService) {
        this.connectionService = Objects.requireNonNull(connectionService);
        setSizeFull();
        HorizontalLayout row1 = new HorizontalLayout(host, port, unitId);
        HorizontalLayout row2 = new HorizontalLayout(start, count, read);
        HorizontalLayout row3 = new HorizontalLayout(writeAddress, writeValue, write);
        HorizontalLayout row4 = new HorizontalLayout(connect, disconnect, manage, status, progress);

        console.setWidthFull();
        console.setHeight("320px");
        console.setReadOnly(true);

        progress.setVisible(false);

        // initial button state
        disconnect.setEnabled(false);
        read.setEnabled(false);
        write.setEnabled(false);

        add(row1, row2, row3, row4, console);

        connect.addClickListener(e -> doConnect());
        disconnect.addClickListener(e -> doDisconnect());
        read.addClickListener(e -> doRead());
        write.addClickListener(e -> doWrite());
        manage.addClickListener(e -> doManage());
    }

    private void doManage() {
        String h = host.getValue();
        int p;
        int u;
        try {
            p = Integer.parseInt(port.getValue());
            u = Integer.parseInt(unitId.getValue());
        } catch (NumberFormatException ex) {
            Notification.show("Invalid port or unit id");
            return;
        }
        appendConsole("Adding managed connection " + h + ":" + p + " unit=" + u);
        ModbusUnit mu = connectionService.addUnit(h, p, u);
        connectionService.connectAsync(mu, defaultTimeoutMs);
    }

    private void doConnect() {
        String h = host.getValue();
        int p;
        try {
            p = Integer.parseInt(port.getValue());
        } catch (NumberFormatException ex) {
            Notification.show("Invalid port");
            return;
        }

        // update UI to show connecting
        appendConsole("Connecting to " + h + ":" + p + " ...");
        status.setText("Connecting...");
        progress.setVisible(true);
        progress.setIndeterminate(true);
        connect.setEnabled(false);
        disconnect.setEnabled(true);
        read.setEnabled(false);
        write.setEnabled(false);

        final UI ui = UI.getCurrent();
        connecting = true;
        // start countdown for timeout display
        final int timeoutMs = defaultTimeoutMs;
        CompletableFuture.runAsync(() -> {
            int seconds = Math.max(1, timeoutMs / 1000);
            for (int rem = seconds; rem >= 0 && connecting; rem--) {
                int r = rem;
                ui.access(() -> appendConsole("Connect timeout in " + r + "s"));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                ModbusTcpClient c = new ModbusTcpClient(h, p, timeoutMs);
                client = c;
                connecting = false;
                ui.access(() -> {
                    appendConsole("Connected");
                    status.setText("Connected");
                    progress.setVisible(false);
                    connect.setEnabled(false);
                    disconnect.setEnabled(true);
                    read.setEnabled(true);
                    write.setEnabled(true);
                    ui.push();
                });
            } catch (IOException ex) {
                connecting = false;
                ui.access(() -> {
                    appendConsole("Connect failed: " + ex.getMessage());
                    status.setText("Connect failed: " + ex.getMessage());
                    progress.setVisible(false);
                    connect.setEnabled(true);
                    disconnect.setEnabled(false);
                    ui.push();
                });
            }
        });
    }

    private void doDisconnect() {
        appendConsole("Disconnecting...");
        status.setText("Disconnecting...");
        progress.setVisible(true);
        progress.setIndeterminate(true);
        connect.setEnabled(false);
        disconnect.setEnabled(false);
        read.setEnabled(false);
        write.setEnabled(false);

        final UI ui = UI.getCurrent();
        CompletableFuture.runAsync(() -> {
            if (client != null) {
                try {
                    client.close();
                    client = null;
                    ui.access(() -> {
                        appendConsole("Disconnected");
                        status.setText("Disconnected");
                        progress.setVisible(false);
                        connect.setEnabled(true);
                        disconnect.setEnabled(false);
                        read.setEnabled(false);
                        write.setEnabled(false);
                    });
                } catch (Exception ex) {
                    ui.access(() -> {
                        appendConsole("Error closing: " + ex.getMessage());
                        status.setText("Error closing: " + ex.getMessage());
                        progress.setVisible(false);
                        connect.setEnabled(true);
                    });
                }
            } else {
                ui.access(() -> appendConsole("Not connected"));
                ui.access(() -> {
                    status.setText("Not connected");
                    progress.setVisible(false);
                    connect.setEnabled(true);
                });
            }
        });
    }

    private void doRead() {
        if (client == null) {
            Notification.show("Not connected");
            return;
        }
        int u, s, ccount;
        try {
            u = Integer.parseInt(unitId.getValue());
            s = Integer.parseInt(start.getValue());
            ccount = Integer.parseInt(count.getValue());
        } catch (NumberFormatException ex) {
            Notification.show("Invalid numeric input");
            return;
        }

        int originalStart = s;
        s = toZeroBasedRegister(s);

        appendConsole("Reading holding registers unit=" + u + " start=" + originalStart + " (-> " + s + ") count=" + ccount);
        status.setText("Reading...");
        progress.setVisible(true);
        progress.setIndeterminate(true);
        read.setEnabled(false);
        write.setEnabled(false);

        final int unitF = u;
        final int startF = s;
        final int countF = ccount;

        final UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> {
            try {
                return client.readHoldingRegisters(unitF, startF, countF);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((regs, ex) -> {
            if (ex != null) {
                ui.access(() -> {
                    appendConsole("Read error: " + ex.getCause().getMessage());
                    status.setText("Read error: " + ex.getCause().getMessage());
                    progress.setVisible(false);
                    read.setEnabled(true);
                    write.setEnabled(true);
                    ui.push();
                });
            } else {
                ui.access(() -> {
                    appendConsole("Read result: " + Arrays.toString(regs));
                    status.setText("Last read OK");
                    progress.setVisible(false);
                    read.setEnabled(true);
                    write.setEnabled(true);
                    ui.push();
                });
            }
        });
    }

    private void doWrite() {
        if (client == null) {
            Notification.show("Not connected");
            return;
        }
        int u, addr, val;
        try {
            u = Integer.parseInt(unitId.getValue());
            addr = Integer.parseInt(writeAddress.getValue());
            val = Integer.parseInt(writeValue.getValue());
        } catch (NumberFormatException ex) {
            Notification.show("Invalid numeric input");
            return;
        }
        int originalAddr = addr;
        addr = toZeroBasedRegister(addr);

        appendConsole("Writing unit=" + u + " address=" + originalAddr + " (-> " + addr + ") value=" + val);
        status.setText("Writing...");
        progress.setVisible(true);
        progress.setIndeterminate(true);
        read.setEnabled(false);
        write.setEnabled(false);

        final int unitF = u;
        final int addrF = addr;
        final int valF = val;

        final UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> {
            try {
                return client.writeSingleRegister(unitF, addrF, valF);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((ok, ex) -> {
            if (ex != null) {
                ui.access(() -> {
                    appendConsole("Write error: " + ex.getCause().getMessage());
                    status.setText("Write error: " + ex.getCause().getMessage());
                    progress.setVisible(false);
                    read.setEnabled(true);
                    write.setEnabled(true);
                    ui.push();
                });
            } else {
                ui.access(() -> {
                    appendConsole("Write result: " + ok);
                    status.setText("Last write OK");
                    progress.setVisible(false);
                    read.setEnabled(true);
                    write.setEnabled(true);
                    ui.push();
                });
            }
        });
    }

    /**
     * Converts a human Modbus address (e.g. 40001) to a zero-based register index.
     * If the input already looks like a zero-based index (< 100000 and < 40000),
     * it is returned unchanged. For addresses in the 40001..49999 range the
     * returned value is address - 40001.
     */
    private int toZeroBasedRegister(int addr) {
        if (addr >= 40001 && addr <= 49999) {
            return addr - 40001; // 40001 -> 0
        }
        if (addr >= 30001 && addr <= 39999) {
            return addr - 30001; // input uses 3xxxx mapping (coils) -> convert similarly if needed
        }
        // otherwise assume already zero-based register index
        return addr;
    }

    private void appendConsole(String line) {
        String prev = console.getValue();
        if (prev == null || prev.isEmpty()) {
            console.setValue(line);
        } else {
            console.setValue(prev + "\n" + line);
        }
    }
}
