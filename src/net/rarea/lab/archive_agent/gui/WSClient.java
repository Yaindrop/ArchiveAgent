package net.rarea.lab.archive_agent.gui;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;

class WSClient extends Thread {

    private InputStream inputStream;
    private OutputStream outputStream;
    private String id;
    private GUI gui;
    private WSServer server;
    private Socket client;

    WSClient(GUI gui, WSServer server, Socket client) {
        this.gui = gui;
        this.server = server;
        this.client = client;
        setName("AA-WSClient-Uninitialized");
    }

    public void run() {
        try {
            inputStream  = client.getInputStream();
            outputStream  = client.getOutputStream();
            id = handShake();
            setName("AA-WSClient-" + id);
            server.sendPrevLogs(this);
            gui.serverLogPrintln("Connection established @ " + id);
        } catch(IOException e){
            gui.serverLogPrintln("Connection error @ " + id);
            e.printStackTrace();
            closeConnection();
            return;
        }
        try {
            for (String message; (message = waitMessage()).compareTo("\u0003\ufffd") != 0; )
                handle(message);
        } catch (SocketException e) {
            gui.serverLogPrintln("Connection terminated abruptly @ " + id);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            closeConnection();
        }
    }

    private void handle(String message) {
        gui.serverLogPrintln("Message received @ " + id + ": " + message);
        server.handle(this, message);
    }

    void send(String message) {
        try {
            outputStream.write(encode(message));
            outputStream.flush();
            System.out.println("Message sent @ " + id + ": " + message);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Message sending error @ " + id + ": " + message);
        }
    }

    synchronized void closeConnection() {
        try {
            if (inputStream != null) inputStream.close();
            if (client != null) outputStream.close();
            if (client != null) client.close();
            server.removeClient(this);
            gui.serverLogPrintln("Connection closed @ " + id);
        } catch(IOException e){
            gui.serverLogPrintln("Connection close error @ " + id);
            e.printStackTrace();
        }
    }

    // Source for encoding and decoding codes:
    // https://stackoverflow.com/questions/8125507/how-can-i-send-and-receive-websocket-messages-on-the-server-side

    private String waitMessage() throws IOException {
        int len = 0;
        byte[] b = new byte[1024];
        len = inputStream.read(b);
        if (len != -1) {
            byte rLength = 0;
            int rMaskIndex = 2;
            int rDataStart = 0;
            //b[0] is always text in my case so no need to check;
            byte data = b[1];
            byte op = (byte) 127;
            rLength = (byte) (data & op);

            if (rLength == (byte) 126) rMaskIndex = 4;
            if (rLength == (byte) 127) rMaskIndex = 10;

            byte[] masks = new byte[4];

            int j = 0;
            int i = 0;
            for (i = rMaskIndex; i < (rMaskIndex + 4); i ++) {
                masks[j] = b[i];
                j ++;
            }

            rDataStart = rMaskIndex + 4;

            int messLen = len - rDataStart;

            byte[] message = new byte[messLen];

            for (i = rDataStart, j = 0; i < len; i ++, j ++) {
                message[j] = (byte) (b[i] ^ masks[j % 4]);
            }

            return new String(message, StandardCharsets.UTF_8);
        }
        return "";
    }

    private byte[] encode(String message) {
        byte[] rawData = message.getBytes(StandardCharsets.UTF_8);

        int frameCount  = 0;
        byte[] frame = new byte[10];

        frame[0] = (byte) 129;

        if (rawData.length <= 125) {
            frame[1] = (byte) rawData.length;
            frameCount = 2;
        } else if (rawData.length <= 65535) {
            frame[1] = (byte) 126;
            int len = rawData.length;
            frame[2] = (byte)((len >> 8 ) & (byte)255);
            frame[3] = (byte)(len & (byte)255);
            frameCount = 4;
        } else {
            frame[1] = (byte) 127;
            int len = rawData.length;
            frame[2] = (byte)((len >> 56 ) & (byte)255);
            frame[3] = (byte)((len >> 48 ) & (byte)255);
            frame[4] = (byte)((len >> 40 ) & (byte)255);
            frame[5] = (byte)((len >> 32 ) & (byte)255);
            frame[6] = (byte)((len >> 24 ) & (byte)255);
            frame[7] = (byte)((len >> 16 ) & (byte)255);
            frame[8] = (byte)((len >> 8 ) & (byte)255);
            frame[9] = (byte)(len & (byte)255);
            frameCount = 10;
        }

        int bLength = frameCount + rawData.length;

        byte[] reply = new byte[bLength];

        int bLim = 0;
        for (int i = 0; i < frameCount; i ++){
            reply[bLim] = frame[i];
            bLim++;
        }
        for (int i = 0; i < rawData.length; i ++){
            reply[bLim] = rawData[i];
            bLim++;
        }

        return reply;
    }

    private String handShake() throws IOException {
        String data = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\r\\n\\r\\n").next();
        Matcher get = Pattern.compile("^GET").matcher(data);
        if (get.find()) {
            Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
            match.find();
            try {
                String id = DatatypeConverter.printBase64Binary(
                        MessageDigest.getInstance("SHA-1")
                                .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                        .getBytes(StandardCharsets.UTF_8)));
                byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Sec-WebSocket-Accept: "
                        + id + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                outputStream.write(response, 0, response.length);
                return id;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
