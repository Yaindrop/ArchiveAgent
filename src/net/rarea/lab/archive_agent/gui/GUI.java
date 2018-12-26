package net.rarea.lab.archive_agent.gui;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.rarea.lab.archive_agent.Utils;
import net.rarea.lab.archive_agent.agent.Agent;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;

import java.util.LinkedList;
import java.util.Arrays;
import java.awt.*;

public class GUI {
    private TrayIcon trayIcon;

    Path agentPath;
    Agent agent;

    private Path httpRoot;
    private int httpPort = 8000, wsPort;
    private HttpServer httpServer;
    public WSServer wsServer;

    LinkedList<StringBuilder> serverLog;
    LinkedList<StringBuilder> agentLog;

    JFrame invisibleTop;
    private Path guiJson;
    boolean initialized = false;


    public GUI() throws IOException {
        serverLog = new LinkedList<>();
        serverLog.add(new StringBuilder());
        agentLog = new LinkedList<>();
        agentLog.add(new StringBuilder());

        invisibleTop = new JFrame();
        invisibleTop.setAlwaysOnTop(true);
        invisibleTop.setVisible(false);
        guiJson = Paths.get("gui.json");

        MenuItem stop = new MenuItem("Quit");
        stop.addActionListener(e -> {
            quit();
        });
        PopupMenu menu = new PopupMenu();
        menu.add(stop);

        Image image = Toolkit.getDefaultToolkit().getImage("client/icon.png");
        trayIcon = new TrayIcon(image, "Archive Agent", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> {
            openHomePage();
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.out.println("AWTException: " + e.toString());
            for (StackTraceElement ste : e.getStackTrace()) System.out.println("\t" + ste);
        }

        httpRoot = Paths.get("client");
        httpPort = setHttpServer();
        wsServer = new WSServer(this);
        wsServer.start();
        wsPort = wsServer.port;
        passWSPort();

        readGuiJson();
        if (agentPath != null) initializeAgent();
    }

    private void readGuiJson() throws IOException {
        if (Files.exists(guiJson)) {
            JsonObject obj = new JsonParser().parse(new FileReader(guiJson.toFile())).getAsJsonObject();
            Path archive_folder = Paths.get(obj.get("archive_folder").getAsString());
            if (isValidAgentPath(archive_folder)) agentPath = archive_folder;
        }
    }

    boolean isValidAgentPath(Path dir) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            else if (Files.walk(dir).anyMatch(p -> !dir.equals(p))) {
                if (Files.exists(dir.resolve("database/status.json"))) {
                    // TODO: Consider more
                    return true;
                } else return false;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    void updateAgentPath(Path dir) {
        agentPath = dir;
        JsonObject obj = new JsonObject();
        obj.addProperty("archive_folder", agentPath.toString());
        Utils.writeFile(obj.toString(), guiJson);
    }

    void initializeAgent() {
        agent = new Agent(this, agentPath);
        agent.start();
        JsonObject obj = new JsonObject();
        obj.addProperty("archive_folder", agentPath.toString());
        wsServer.sendAll(Utils.makeMessage("agent_initialized", obj));
        initialized = true;
    }

    private void openHomePage() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + httpPort));
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    private void stopServers() {
        httpServer.stop(0);
        wsServer.stopServer();
    }

    void quit() {
        if (initialized) agent.stopAgent();
        stopServers();
        SystemTray.getSystemTray().remove(trayIcon);
        invisibleTop.dispose();
        System.exit(0);
    }

    private void passWSPort() {
        JsonObject json = new JsonObject();
        json.addProperty("wsPort", wsPort);
        Utils.writeFile(json.toString(), httpRoot.resolve("wsport.json"));
    }

    private int setHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", t -> {
            OutputStream os = t.getResponseBody();
            Path localFile =  httpRoot.resolve(t.getRequestURI().toString().substring(1));
            if (!Files.exists(localFile)) {
                t.sendResponseHeaders(404, 0);
                os.close();
                return;
            }
            if (Files.isDirectory(localFile)) localFile = localFile.resolve("index.html");

            FileChannel inChannel =  FileChannel.open(localFile);
            t.sendResponseHeaders(200, inChannel.size());
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            for (int bytesRead; (bytesRead = inChannel.read(buffer)) > 0; ) {
                byte[] data = (bytesRead == buffer.capacity() ? buffer.array() : Arrays.copyOfRange(buffer.array(), 0, bytesRead));
                os.write(data);
                buffer.clear();
            }
            inChannel.close();
            os.close();
        });
        httpServer.setExecutor(null); // creates a default executor
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    Path requestLocalDir(String title) {
        Path res = null;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(title);
        int result = chooser.showDialog(invisibleTop, "Choose Folder");

        if (result == JFileChooser.APPROVE_OPTION)
            res = chooser.getSelectedFile().toPath();
        return res;
    }

    public void serverLogPrint(String log) {
        System.out.print(log);
        serverLog.getLast().append(log);
        wsServer.sendAll(Utils.makeMessage("server_log_print", new JsonPrimitive(log)));
    }

    public void agentLogPrint(String log) {
        System.out.print(log);
        agentLog.getLast().append(log);
        wsServer.sendAll(Utils.makeMessage("agent_log_print", new JsonPrimitive(log)));
    }

    public void serverLogPrintln(String log) {
        System.out.println(log);
        serverLog.getLast().append(log);
        serverLog.addLast(new StringBuilder());
        wsServer.sendAll(Utils.makeMessage("server_log_println", new JsonPrimitive(log)));
    }

    public void agentLogPrintln(String log) {
        System.out.println(log);
        agentLog.getLast().append(log);
        agentLog.addLast(new StringBuilder());
        wsServer.sendAll(Utils.makeMessage("agent_log_println", new JsonPrimitive(log)));
    }
}
