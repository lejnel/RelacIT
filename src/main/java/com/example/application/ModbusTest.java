package com.example.application;

/**
 * Simple CLI test to verify Modbus TCP connectivity.
 *
 * Usage:
 *   java -cp target/relac-it-1.0-SNAPSHOT.jar com.example.application.ModbusTest [host] [port] [unitId] [start] [count]
 * Defaults: host=192.168.0.130 port=502 unitId=1 start=0 count=2
 */
public class ModbusTest {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "192.168.0.130";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5020;
        int unit = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int start = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int count = args.length > 4 ? Integer.parseInt(args[4]) : 2;

        System.out.println("Attempting Modbus TCP connection to " + host + ":" + port + " unit=" + unit);
        try (ModbusTcpClient client = new ModbusTcpClient(host, port)) {
            int[] regs = client.readHoldingRegisters(unit, start, count);
            System.out.println("Read registers: ");
            for (int i = 0; i < regs.length; i++) {
                System.out.println("  [" + (start + i) + "] = " + regs[i]);
            }

            // try a write test (writes to 'start')
            System.out.println("Writing value 123 to register " + start + "...");
            boolean ok = client.writeSingleRegister(unit, start, 123);
            System.out.println("Write OK: " + ok);
        } catch (Exception e) {
            System.err.println("Modbus test failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(2);
        }

        System.out.println("Modbus test completed.");
    }
}
