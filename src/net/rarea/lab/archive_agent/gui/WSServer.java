package net.rarea.lab.archive_agent.gui;

import com.google.gson.*;
import net.rarea.lab.archive_agent.Utils;
import net.rarea.lab.archive_agent.agent.*;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class WSServer extends Thread {
    private GUI gui;
    private ServerSocket server;
    private Set<WSClient> clients;
    private boolean stopped = false;

    int port;

    WSServer(GUI gui) throws IOException {
        this.gui = gui;
        server = new ServerSocket(0);
        clients = new HashSet<>();
        port = server.getLocalPort();
        setName("AA-WebSocket Server");
    }

    @Override
    public void run() {
        gui.serverLogPrintln("WebSocket Server Started at " + System.currentTimeMillis());
        while(!stopped) {
            try {
                Socket clientSocket = server.accept();
                WSClient client = new WSClient(gui,this, clientSocket);
                clients.add(client);
                client.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void sendPrevLogs(WSClient client) {
        JsonArray prevServerLogs = new JsonArray();
        JsonArray prevAgentLogs = new JsonArray();
        for (StringBuilder log : gui.serverLog) prevServerLogs.add(log.toString());
        for (StringBuilder log : gui.agentLog) prevAgentLogs.add(log.toString());
        JsonObject obj = new JsonObject();
        obj.add("prev_server_logs", prevServerLogs);
        obj.add("prev_agent_logs", prevAgentLogs);
        client.send(Utils.makeMessage("prev_log", obj).toString());
    }

    public void agentRecordsUpdated(Record r) {
        if (!gui.initialized) return;
    }

    void handle(WSClient client, String message) {
        JsonObject obj = new JsonParser().parse(message).getAsJsonObject();
        String type = obj.get("type").getAsString();
        JsonObject reply = new JsonObject();
        reply.add("type", obj.get("type"));
        switch (type) { // pre-initialization
            case "check_status": {
                if (gui.initialized) {
                    reply.addProperty("reply", true);
                    reply.addProperty("archive_folder", gui.agentPath.toString());
                    gui.agent.statusUpdated();
                } else {
                    reply.addProperty("reply", false);
                }
                break;
            } case "initialize_archive_path": {
                Path dir = gui.requestLocalDir("Choose New Archive Folder");
                if (dir == null) return;
                if (gui.isValidAgentPath(dir)) {
                    gui.updateAgentPath(dir);
                    gui.initializeAgent();
                    reply.addProperty("reply", true);
                } else {
                    reply.addProperty("reply", false);
                    JOptionPane.showMessageDialog(gui.invisibleTop, "Not a valid archive folder.", "Error", JOptionPane.ERROR_MESSAGE);
                }
                break;
            } case "quit": {
                gui.quit();
            }
        }
        if (gui.initialized) {
            switch (type) { // post-initialization
                // Overview
                case "open_archive_path": {
                    Utils.openLocalDir(gui.agentPath);
                    break;
                } case "move_archive_folder": {
                    JOptionPane.showMessageDialog(gui.invisibleTop, "Under Development", "Notice", JOptionPane.INFORMATION_MESSAGE);
                    break;
                } case "pause_syncing": {
                    gui.agent.setSyncing(false);
                    break;
                } case "resume_syncing": {
                    gui.agent.setSyncing(true);
                    break;
                } case "add_watching_folder": {
                    Path dir = gui.requestLocalDir("Choose New Watching Folder");
                    if (dir == null) return;
                    gui.agent.addWatchingDir(dir);
                    break;
                }
                // Watching Folder
                case "check_watching_folder":
                case "recheck_file_list": {
                    Path requestedPath = Paths.get(obj.get("content").getAsString());
                    JsonObject records = gui.agent.getAllRecords(requestedPath);
                    if (records != null) reply.add("reply", records);
                    break;
                }
                case "file_restore": {
                    Path file = Paths.get(obj.get("content").getAsJsonObject().get("path").getAsString());
                    if (!gui.agent.isValidFile(file)) return;
                    Path dir = gui.requestLocalDir("Save file to");
                    if (dir == null) return;
                    gui.agent.restoreFile(file, dir);
                    Utils.openLocalDir(dir);
                    break;
                } case "file_discard_all": {
                    Path file = Paths.get(obj.get("content").getAsJsonObject().get("path").getAsString());
                    if (!gui.agent.isValidFile(file)) return;
                    gui.agent.discardOutdatedRecordsOf(file);
                    gui.agent.dataUpdated();
                    break;
                } case "file_clean": {
                    Path file = Paths.get(obj.get("content").getAsJsonObject().get("path").getAsString());
                    if (!gui.agent.isValidFile(file)) return;
                    if (!gui.agent.isActiveFile(file)) gui.agent.discardFile(file);
                    gui.agent.dataUpdated();
                    break;
                } case "record_restore": {
                    Record record = gui.agent.getRecord(
                            Paths.get(obj.get("content").getAsJsonObject().get("original").getAsString()),
                            obj.get("content").getAsJsonObject().get("id").getAsString());
                    if (record == null) return;
                    Path dir = gui.requestLocalDir("Save file to");
                    if (dir == null) return;
                    gui.agent.restoreRecord(record, dir);
                    Utils.openLocalDir(dir);
                    break;
                } case "record_discard": {
                    Record record = gui.agent.getRecord(
                            Paths.get(obj.get("content").getAsJsonObject().get("original").getAsString()),
                            obj.get("content").getAsJsonObject().get("id").getAsString());
                    if (gui.agent.isOnlyRecord(record)) {
                        JOptionPane.showMessageDialog(gui.invisibleTop, "The only record cannot be discarded", "Notice", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    gui.agent.discardRecord(record);
                    gui.agent.dataUpdated();
                    break;
                } case "restore_all": {
                    Path dir = Paths.get(obj.get("content").getAsString());
                    Path target = gui.requestLocalDir("Save files to");
                    if (target == null) return;
                    gui.agent.restoreDir(dir, target.resolve(dir.getFileName()));
                    Utils.openLocalDir(target.resolve(dir.getFileName()));
                    break;
                } case "discard_all": {
                    Path dir = Paths.get(obj.get("content").getAsString());
                    gui.agent.discardAllOutdatedRecords(dir);
                    gui.agent.dataUpdated();
                    break;
                } case "clean_inactive": {
                    Path dir = Paths.get(obj.get("content").getAsString());
                    gui.agent.discardAllInactive(dir);
                    gui.agent.dataUpdated();
                    break;
                } case "stop_watching": {
                    Path dir = Paths.get(obj.get("content").getAsString());
                    if (gui.agent.isValidWatchingDir(dir)) gui.agent.removeWatchingDir(dir);
                    break;
                }
                // Settings
                case "settings_change": {
                    break;
                }
            }
        }
        client.send(Utils.makeMessage("reply", reply).toString());
    }

    public void sendAll(JsonObject obj) {
        for (WSClient client : clients) client.send(obj.toString());
    }

    void removeClient(WSClient client) {
        clients.remove(client);
    }

    void stopServer() {
        stopped = true;
        for (WSClient client : clients) client.closeConnection();
    }
}
