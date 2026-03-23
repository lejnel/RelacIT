package com.example.application.s7;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Siemens S7 PLC client using S7Comm protocol (ISO-on-TCP).
 * Supports S7-300, S7-400, S7-1200, and S7-1500 PLCs.
 * 
 * <p>Usage example:
 * <pre>
 * try (S7Client client = new S7Client("192.168.0.1", 0, 1)) {
 *     client.connect();
 *     byte[] data = client.readDB(1, 0, 10); // Read DB1.DBW0, 10 bytes
 *     int value = S7Client.getWord(data, 0);
 * }
 * </pre>
 * 
 * Based on S7Comm protocol documentation and Snap7/LibNoDave implementations.
 */
public class S7Client implements AutoCloseable {
    
    public static final int S7_PORT = 102;
    
    private static final int ISO_MTU = 240;
    private static final byte PDU_TYPE_PUSH = 0x00;
    
    // S7 Area types
    public static final byte S7AREA_PE = (byte) 0x81;  // Process inputs
    public static final byte S7AREA_PA = (byte) 0x82;  // Process outputs
    public static final byte S7AREA_MK = (byte) 0x83;  // Merkers (M memory)
    public static final byte S7AREA_DB = (byte) 0x84;  // Data blocks
    public static final byte S7AREA_CT = (byte) 0x1C;  // Counters
    public static final byte S7AREA_TM = (byte) 0x1D;  // Timers
    
    // S7 Word lengths
    public static final byte S7WL_BIT = 0x01;
    public static final byte S7WL_BYTE = 0x02;
    public static final byte S7WL_WORD = 0x04;
    public static final byte S7WL_DWORD = 0x06;
    public static final byte S7WL_REAL = 0x08;
    
    private final String host;
    private final int rack;
    private final int slot;
    private final int timeoutMs;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final AtomicInteger transactionId = new AtomicInteger(0);
    
    private int pduSize = 480;
    private boolean connected = false;
    
    public S7Client(String host) {
        this(host, 0, 1, 5000);
    }
    
    public S7Client(String host, int rack, int slot) {
        this(host, rack, slot, 5000);
    }
    
    public S7Client(String host, int rack, int slot, int timeoutMs) {
        this.host = host;
        this.rack = rack;
        this.slot = slot;
        this.timeoutMs = timeoutMs;
    }
    
    /**
     * Connect to the PLC and negotiate parameters.
     */
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, S7_PORT), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);
        
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        
        // ISO connection request (COTP)
        sendConnectionRequest();
        receiveConnectionConfirm();
        
        // S7 communication setup
        sendCommSetup();
        receiveCommSetupAck();
        
        connected = true;
        System.out.println("S7Client: Connected to " + host + " (Rack=" + rack + ", Slot=" + slot + ")");
    }
    
    private void sendConnectionRequest() throws IOException {
        // COTP Connection Request
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // TPKT header
        buffer.put((byte) 0x03);  // Version
        buffer.put((byte) 0x00);  // Reserved
        buffer.putShort((short) 22);  // Length
        
        // COTP header
        buffer.put((byte) 16);    // Length
        buffer.put((byte) 0xE0);  // PDU type: Connection Request
        buffer.put((byte) 0x00);  // Destination reference
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);  // Source reference
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);  // Class option
        
        // Source TSAP (TSAP ID)
        buffer.put((byte) 0xC1);  // Parameter code: Source TSAP
        buffer.put((byte) 0x02);  // Length
        buffer.put((byte) 0x01);  // Local TSAP
        buffer.put((byte) 0x00);
        
        // Destination TSAP
        buffer.put((byte) 0xC2);  // Parameter code: Destination TSAP
        buffer.put((byte) 0x02);  // Length
        buffer.put((byte) (rack * 32 + slot));  // Rack/Slot encoded
        buffer.put((byte) 0x00);
        
        // PDU size negotiation
        buffer.put((byte) 0xC0);  // Parameter code: TPDU size
        buffer.put((byte) 0x01);  // Length
        buffer.put((byte) 0x0A);  // 1024 bytes
        
        out.write(buffer.array());
        out.flush();
    }
    
    private void receiveConnectionConfirm() throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        
        // Verify TPKT
        if (header[0] != 0x03) {
            throw new IOException("Invalid TPKT header");
        }
        
        int length = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        byte[] response = new byte[length - 4];
        in.readFully(response);
        
        // Check for COTP Connection Confirm (0xD0)
        if (response[0] != (byte) 0xD0) {
            throw new IOException("Expected Connection Confirm, got: " + String.format("0x%02X", response[0]));
        }
    }
    
    private void sendCommSetup() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(29);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // TPKT
        buffer.put((byte) 0x03);
        buffer.put((byte) 0x00);
        buffer.putShort((short) 29);
        
        // COTP Data
        buffer.put((byte) 0x02);  // Length
        buffer.put((byte) 0xF0);  // PDU type: Data
        buffer.put((byte) 0x80);  // EOT
        
        // S7 Header
        buffer.put((byte) 0x32);  // S7 Protocol ID
        buffer.put((byte) 0x01);  // Job type
        buffer.putShort((short) transactionId.incrementAndGet());  // Transaction ID
        buffer.putShort((short) 0);  // PDU reference
        buffer.putShort((short) 8);  // Parameter length
        buffer.putShort((short) 0);  // Data length
        
        // S7 Parameters
        buffer.put((byte) 0xF0);  // Function: Setup communication
        buffer.put((byte) 0x00);
        buffer.putShort((short) 1);  // Max AMQ caller
        buffer.putShort((short) 1);  // Max AMQ callee
        buffer.putShort((short) pduSize);  // PDU size
        buffer.putShort((short) 0);  // Reserved
        
        out.write(buffer.array());
        out.flush();
    }
    
    private void receiveCommSetupAck() throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        
        byte[] response = new byte[length - 4];
        in.readFully(response);
        
        // Extract negotiated PDU size
        if (response.length >= 20) {
            int negoPduSize = ((response[18] & 0xFF) << 8) | (response[19] & 0xFF);
            if (negoPduSize > 0) {
                pduSize = Math.min(pduSize, negoPduSize);
            }
        }
    }
    
    /**
     * Read bytes from a Data Block (DB).
     * 
     * @param dbNumber Data block number
     * @param startByte Starting byte offset
     * @param count Number of bytes to read
     * @return Raw byte array from PLC
     */
    public byte[] readDB(int dbNumber, int startByte, int count) throws IOException {
        return readArea(S7AREA_DB, dbNumber, startByte, count, S7WL_BYTE);
    }
    
    /**
     * Read from any S7 area.
     * 
     * @param area S7 area code (S7AREA_*)
     * @param dbNumber Data block number (0 for non-DB areas)
     * @param start Starting address
     * @param count Number of elements
     * @param wordLen Element size code
     * @return Raw byte array
     */
    public byte[] readArea(byte area, int dbNumber, int start, int count, byte wordLen) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to PLC");
        }
        
        // Build read request
        ByteBuffer request = ByteBuffer.allocate(31);
        request.order(ByteOrder.BIG_ENDIAN);
        
        // S7 header
        request.put((byte) 0x32);  // Protocol ID
        request.put((byte) 0x01);  // Job type
        request.putShort((short) transactionId.incrementAndGet());
        request.putShort((short) 0);  // PDU ref
        request.putShort((short) 12); // Parameter length
        request.putShort((short) 0);  // Data length
        
        // Parameters
        request.put((byte) 0x04);  // Function: Read
        request.put((byte) 0x01);  // Item count
        
        // Item specification
        request.put((byte) 0x12);  // Variable specification
        request.put((byte) 0x0A);  // Length of address specification
        request.put((byte) 0x10);  // Syntax ID: S7ANY
        request.put(wordLen);      // Transport size
        request.putShort((short) count);  // Number of elements
        request.putShort((short) (dbNumber));
        request.put(area);         // Area code
        request.put((byte) 0x00);  // Reserved
        request.put(new byte[3]);  // Address (big-endian 24-bit)
        request.putShort((short) (start * 8)); // Bit address
        
        // Send
        ByteBuffer tpdu = ByteBuffer.allocate(request.limit() + 7);
        tpdu.put((byte) 0x03);
        tpdu.put((byte) 0x00);
        tpdu.putShort((short) (request.limit() + 7));
        tpdu.put((byte) 0x02);  // COTP length
        tpdu.put((byte) 0xF0);  // COTP type
        tpdu.put((byte) 0x80);  // EOT
        tpdu.put(request.array());
        
        out.write(tpdu.array());
        out.flush();
        
        // Receive response
        byte[] responseHeader = new byte[4];
        in.readFully(responseHeader);
        int respLen = ((responseHeader[2] & 0xFF) << 8) | (responseHeader[3] & 0xFF);
        
        byte[] response = new byte[respLen - 4];
        in.readFully(response);
        
        // Parse response
        if (response[8] != (byte) 0x04) {
            throw new IOException("Invalid function code in response");
        }
        
        byte errorClass = response[response.length - 2];
        byte errorCode = response[response.length - 1];
        if (errorClass != 0 || errorCode != (byte) 0xFF) {
            throw new IOException("PLC error: class=" + errorClass + " code=" + errorCode);
        }
        
        // Extract data (skip headers)
        int dataStart = 25;
        int dataLen = response[24] & 0xFF;
        byte[] data = new byte[dataLen];
        System.arraycopy(response, dataStart, data, 0, Math.min(dataLen, response.length - dataStart));
        
        return data;
    }
    
    /**
     * Write bytes to a Data Block.
     */
    public void writeDB(int dbNumber, int startByte, byte[] data) throws IOException {
        writeArea(S7AREA_DB, dbNumber, startByte, data, S7WL_BYTE);
    }
    
    /**
     * Write to any S7 area.
     */
    public void writeArea(byte area, int dbNumber, int start, byte[] data, byte wordLen) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to PLC");
        }
        
        int txId = transactionId.incrementAndGet();
        int paramLen = 12;
        int dataLen = 4 + data.length;
        
        ByteBuffer request = ByteBuffer.allocate(31 + data.length);
        request.order(ByteOrder.BIG_ENDIAN);
        
        // S7 header
        request.put((byte) 0x32);
        request.put((byte) 0x01);
        request.putShort((short) txId);
        request.putShort((short) 0);
        request.putShort((short) paramLen);
        request.putShort((short) dataLen);
        
        // Parameters
        request.put((byte) 0x05);  // Function: Write
        request.put((byte) 0x01);
        request.put((byte) 0x12);
        request.put((byte) 0x0A);
        request.put((byte) 0x10);
        request.put(wordLen);
        request.putShort((short) (data.length / getWordLenSize(wordLen)));
        request.putShort((short) dbNumber);
        request.put(area);
        request.put((byte) 0x00);
        request.put(new byte[3]);
        request.putShort((short) (start * 8));
        
        // Data
        request.put((byte) 0x00);  // Return code: OK
        request.put((byte) getTransportSize(wordLen));
        request.putShort((short) data.length);
        request.put(data);
        
        // Send (with TPKT/COTP wrapper)
        ByteBuffer tpdu = ByteBuffer.allocate(request.limit() + 7);
        tpdu.put((byte) 0x03);
        tpdu.put((byte) 0x00);
        tpdu.putShort((short) (request.limit() + 7));
        tpdu.put((byte) 0x02);
        tpdu.put((byte) 0xF0);
        tpdu.put((byte) 0x80);
        tpdu.put(request.array());
        
        out.write(tpdu.array());
        out.flush();
        
        // Read response
        byte[] header = new byte[4];
        in.readFully(header);
        int len = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        byte[] response = new byte[len - 4];
        in.readFully(response);
        
        // Check result
        byte errorClass = response[response.length - 2];
        byte errorCode = response[response.length - 1];
        if (errorClass != 0 || errorCode != (byte) 0xFF) {
            throw new IOException("PLC write error: class=" + errorClass + " code=" + errorCode);
        }
    }
    
    private int getWordLenSize(byte wordLen) {
        return switch (wordLen) {
            case S7WL_BIT -> 1;
            case S7WL_BYTE -> 1;
            case S7WL_WORD -> 2;
            case S7WL_DWORD -> 4;
            case S7WL_REAL -> 4;
            default -> 1;
        };
    }
    
    private byte getTransportSize(byte wordLen) {
        return switch (wordLen) {
            case S7WL_BIT -> 0x01;
            case S7WL_BYTE -> 0x02;
            case S7WL_WORD -> 0x04;
            case S7WL_DWORD -> 0x06;
            case S7WL_REAL -> 0x08;
            default -> 0x02;
        };
    }
    
    // === Utility methods for data conversion ===
    
    public static boolean getBit(byte[] buffer, int byteIndex, int bitIndex) {
        return (buffer[byteIndex] & (1 << bitIndex)) != 0;
    }
    
    public static void setBit(byte[] buffer, int byteIndex, int bitIndex, boolean value) {
        if (value) {
            buffer[byteIndex] |= (1 << bitIndex);
        } else {
            buffer[byteIndex] &= ~(1 << bitIndex);
        }
    }
    
    public static int getByte(byte[] buffer, int index) {
        return buffer[index] & 0xFF;
    }
    
    public static int getWord(byte[] buffer, int index) {
        return ((buffer[index] & 0xFF) << 8) | (buffer[index + 1] & 0xFF);
    }
    
    public static int getDWord(byte[] buffer, int index) {
        return ((buffer[index] & 0xFF) << 24) |
               ((buffer[index + 1] & 0xFF) << 16) |
               ((buffer[index + 2] & 0xFF) << 8) |
               (buffer[index + 3] & 0xFF);
    }
    
    public static float getReal(byte[] buffer, int index) {
        int bits = getDWord(buffer, index);
        return Float.intBitsToFloat(bits);
    }
    
    public static void setWord(byte[] buffer, int index, int value) {
        buffer[index] = (byte) (value >> 8);
        buffer[index + 1] = (byte) value;
    }
    
    public static void setDWord(byte[] buffer, int index, int value) {
        buffer[index] = (byte) (value >> 24);
        buffer[index + 1] = (byte) (value >> 16);
        buffer[index + 2] = (byte) (value >> 8);
        buffer[index + 3] = (byte) value;
    }
    
    public static void setReal(byte[] buffer, int index, float value) {
        int bits = Float.floatToIntBits(value);
        setDWord(buffer, index, bits);
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void close() {
        connected = false;
        try { if (out != null) out.close(); } catch (Exception e) {}
        try { if (in != null) in.close(); } catch (Exception e) {}
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }
}
