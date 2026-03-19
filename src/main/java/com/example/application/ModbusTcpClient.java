package com.example.application;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Modbus TCP client with basic read/write operations.
 * <p>
 * Usage example:
 * <pre>
 * try (ModbusTcpClient client = new ModbusTcpClient("192.168.0.10", 502)) {
 *     int[] regs = client.readHoldingRegisters(1, 0, 2); // unitId=1, start=0, count=2
 *     client.writeSingleRegister(1, 0, 123);
 * }
 * </pre>
 */
public class ModbusTcpClient implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final AtomicInteger transactionId = new AtomicInteger(0);

    public ModbusTcpClient(String host, int port) throws IOException {
        // delegate to main constructor with default timeout
        this(host, port, 5000);
        System.out.println("ModbusTcpClient: used default timeout 5000ms for " + host + ":" + port);
    }

    public ModbusTcpClient(String host, int port, int timeoutMs) throws IOException {
        System.out.println("ModbusTcpClient: connecting to " + host + ":" + port + " timeout=" + timeoutMs + "ms");
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
        } catch (java.net.SocketTimeoutException ste) {
            System.out.println("ModbusTcpClient: connect timed out to " + host + ":" + port + " after " + timeoutMs + "ms");
            throw new IOException("Connect timed out after " + timeoutMs + "ms", ste);
        } catch (java.net.ConnectException ce) {
            System.out.println("ModbusTcpClient: connect refused to " + host + ":" + port + " -> " + ce.getMessage());
            throw new IOException("Connect failed: " + ce.getMessage(), ce);
        }
        socket.setSoTimeout(timeoutMs);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        System.out.println("ModbusTcpClient: connected to " + host + ":" + port);
    }

    /**
     * Read holding registers (function 0x03).
     *
     * @param unitId slave id
     * @param start  starting address (0-based)
     * @param count  number of registers to read (1..125)
     * @return array of register values as unsigned 16-bit ints
     */
    public int[] readHoldingRegisters(int unitId, int start, int count) throws IOException {
        if (count < 1 || count > 125) {
            throw new IllegalArgumentException("count must be 1..125");
        }

        byte function = 0x03;
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(function);
        pdu.putShort((short) start);
        pdu.putShort((short) count);
        byte[] resp = sendAndReceive(unitId, pdu.array());

        // resp[0] = function
        if ((resp[0] & 0xFF) == (function | 0x80)) {
            throw new IOException("Modbus exception: " + (resp[1] & 0xFF));
        }
        if (resp[0] != function) {
            throw new IOException("Unexpected function code in response: " + (resp[0] & 0xFF));
        }
        int byteCount = resp[1] & 0xFF;
        if (byteCount != count * 2) {
            throw new IOException("Unexpected byte count: " + byteCount);
        }
        int[] registers = new int[count];
        for (int i = 0; i < count; i++) {
            int hi = resp[2 + i * 2] & 0xFF;
            int lo = resp[2 + i * 2 + 1] & 0xFF;
            registers[i] = (hi << 8) | lo;
        }
        return registers;
    }

    /**
     * Write single register (function 0x06).
     * @return true if write echoed back correctly
     */
    public boolean writeSingleRegister(int unitId, int address, int value) throws IOException {
        byte function = 0x06;
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(function);
        pdu.putShort((short) address);
        pdu.putShort((short) value);
        byte[] resp = sendAndReceive(unitId, pdu.array());
        if ((resp[0] & 0xFF) == (function | 0x80)) {
            throw new IOException("Modbus exception: " + (resp[1] & 0xFF));
        }
        // Successful response should echo function/address/value
        if (resp.length < 5) return false;
        int respAddr = ((resp[1] & 0xFF) << 8) | (resp[2] & 0xFF);
        int respVal = ((resp[3] & 0xFF) << 8) | (resp[4] & 0xFF);
        return respAddr == address && respVal == (value & 0xFFFF);
    }

    private synchronized byte[] sendAndReceive(int unitId, byte[] pdu) throws IOException {
        int tx = transactionId.updateAndGet(i -> (i + 1) & 0xFFFF);

        int length = 1 + pdu.length; // Unit id + PDU
        ByteBuffer mbap = ByteBuffer.allocate(7 + pdu.length);
        mbap.putShort((short) tx); // transaction id
        mbap.putShort((short) 0); // protocol id
        mbap.putShort((short) length); // length
        mbap.put((byte) unitId);
        mbap.put(pdu);

        out.write(mbap.array());
        out.flush();

        // Read MBAP header
        byte[] header = new byte[7];
        in.readFully(header);
        ByteBuffer hdr = ByteBuffer.wrap(header);
        int respTx = hdr.getShort() & 0xFFFF;
        int proto = hdr.getShort() & 0xFFFF;
        int respLen = hdr.getShort() & 0xFFFF;
        int respUnit = hdr.get() & 0xFF;
        if (respTx != tx) {
            throw new IOException("Transaction ID mismatch");
        }
        if (proto != 0) {
            throw new IOException("Unsupported protocol id: " + proto);
        }
        int pduLen = respLen - 1; // subtract unit id
        if (pduLen <= 0) {
            throw new IOException("Invalid PDU length: " + pduLen);
        }
        byte[] respPdu = new byte[pduLen];
        in.readFully(respPdu);
        return respPdu;
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (Exception ignored) {
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
