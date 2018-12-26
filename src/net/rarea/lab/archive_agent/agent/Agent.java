package net.rarea.lab.archive_agent.agent;

import com.google.gson.*;
import net.rarea.lab.archive_agent.Utils;
import net.rarea.lab.archive_agent.gui.GUI;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Agent extends Thread {
    private final static int UPDATE = 0, COPY = 1, DELETE = 2;

    private GUI gui;

    private Path agentPath, archivePath, databasePath, statusJsonPath, dataJsonPath;

    private boolean autoSyncing = true;
    private boolean launchOnStart = false;
    private boolean autoDiscardOutdated = false;
    private boolean autoCleanInactive = false;

    // Long-term data containers
    private Map<Path, Set<Record>> original2Records; // All original paths mapped to their related records
    private Map<String, Set<Record>> id2Records; // All archived ids mapped to their related records

    // Run-time data containers
    private Map<Path, Set<Path>> watchingDir2Dirs; // Active original dirs mapped to their sub-dirs
    private Map<Path, String> watchingFile2Id; // Active original paths mapped to their latest ids

    // Run-time data generators
    private Watcher watcher;
    private Queue<WatcherEvent> log;

    // Run-time data handlers
    private int pendingUpdate;
    private Queue<Task> pendingTasks;

    private boolean idle = true;
    private boolean syncing = true;
    private boolean stopped = false;
    private int checkInterval = 1000;

    public Agent(GUI gui, Path dir) {
        try {
            this.gui = gui;

            agentPath = dir;
            databasePath = agentPath.resolve("database/");
            archivePath = agentPath.resolve("archive/");
            statusJsonPath = databasePath.resolve("status.json");
            dataJsonPath = databasePath.resolve("data.json");
            if (!Files.exists(databasePath)) Files.createDirectories(databasePath);
            if (!Files.exists(archivePath)) Files.createDirectories(archivePath);

            original2Records = new HashMap<>();
            id2Records = new HashMap<>();

            watchingDir2Dirs = new HashMap<>();
            watchingFile2Id = new HashMap<>();

            JsonObject dataJson = Utils.readAsJson(dataJsonPath);
            if (dataJson != null) {
                gui.agentLogPrintln("Applying data json");
                applyDataJson(dataJson);
            } else {
                gui.agentLogPrintln("No data json found");
                dataUpdated();
            }

            watcher = new Watcher(gui);
            log = watcher.log;

            pendingUpdate = 0;
            pendingTasks = new ConcurrentLinkedQueue<>();

            JsonObject statusJson = Utils.readAsJson(statusJsonPath);
            if (statusJson != null) {
                gui.agentLogPrintln("Applying status json");
                applyStatusJson(statusJson);
            } else {
                gui.agentLogPrintln("No status json found");
                statusUpdated();
            }

            setName("AA-Agent");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void issueTask(int type, Path from, Path to) {
        Task newTask = new Task(type, from, to);
        gui.agentLogPrint("Issuing task: " + newTask + " ... ");
        boolean applyTask = true;
        if (type == COPY || type == UPDATE) {
            boolean neutralized = pendingTasks.removeIf(newTask::overridePrevDelete);
            if (neutralized) gui.agentLogPrint("overrode previous DELETE ... ");
        }
        if (type == COPY && Files.exists(to)) {
            applyTask = false;
            gui.agentLogPrintln("unnecessary");
        }
        Task same = pendingTasks.stream().filter(newTask::equals).findFirst().orElse(null);
        if (same != null) {
            applyTask = false;
            if (type == UPDATE) {
                pendingTasks.remove(same);
                pendingTasks.add(newTask);
                gui.agentLogPrintln("neutralized");
            } else {
                gui.agentLogPrintln("combined");
            }
        }
        if (applyTask) {
            pendingTasks.add(newTask);
            if (type == UPDATE) pendingUpdate ++;
            gui.agentLogPrintln("issued");
        }
    }

    private void archiveFile(Path file) {
        String id = watchingFile2Id.get(file);
        if (id2Records.containsKey(id)) { // If a record of the same original file and id has been archived, update the record time and return
            id2Records.get(id).stream().filter(r -> r.original.equals(file)).forEach(r -> r.time = System.currentTimeMillis());
            return;
        }
        issueTask(COPY, file, archivePath.resolve(id));
        Record rec = new Record(file, id);

        original2Records.putIfAbsent(file, new HashSet<>());
        original2Records.get(file).add(rec);

        id2Records.putIfAbsent(id, new HashSet<>());
        id2Records.get(id).add(rec);
    }

    private void updateDir(Path dir) {
        Path root = getRootWatchingDir(dir);
        if (root == null) return;
        try {
            Files.walk(dir).filter(p -> Files.isDirectory(p)).forEach(watchingDir2Dirs.get(root)::add);
            Files.walk(dir).filter(p -> !Files.isDirectory(p)).forEach(file -> issueTask(UPDATE, file, null));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addWatchingDir(Path dir) {
        if (!Files.exists(dir)) return;
        watcher.register(dir);
        watchingDir2Dirs.put(dir, new HashSet<>());
        updateDir(dir);
        statusUpdated();
    }

    public void discardRecord(Record r) {
        original2Records.get(r.original).remove(r);
        id2Records.get(r.id).remove(r);
        gui.agentLogPrintln("Record discarded: " + r.id + " of " + r.original);
    }

    public void discardOutdatedRecordsOf(Path file) {
        Set<Record> toDiscard = new HashSet<>();
        Record latest = Collections.max(original2Records.get(file));
        original2Records.get(file).stream().filter(record -> record != latest).forEach(toDiscard::add);
        for (Record r : toDiscard) discardRecord(r);
    }

    public void discardAllOutdatedRecords(Path dir) {
        Set<Path> toDiscard = new HashSet<>();
        original2Records.keySet().stream().filter(file -> file.startsWith(dir)).forEach(toDiscard::add);
        for (Path file : toDiscard) discardOutdatedRecordsOf(file);
    }

    public void discardFile(Path file) {
        Set<Record> toDiscard = new HashSet<>(original2Records.get(file));
        for (Record r : toDiscard) discardRecord(r);
        original2Records.remove(file);
        gui.agentLogPrintln("File discarded: " + file);
    }

    public void discardAllInactive(Path dir) {
        Set<Path> toDiscard = new HashSet<>();
        original2Records.keySet().stream().filter(file -> file.startsWith(dir) && !isActiveFile(file)).forEach(toDiscard::add);
        for (Path file : toDiscard) discardFile(file);
    }


    private void cleanFreeIds() {
        Set<String> freeIds = new HashSet<>();
        id2Records.entrySet().stream().filter(entry -> entry.getValue().isEmpty())
                .forEach(entry -> freeIds.add(entry.getKey()));
        id2Records.keySet().removeIf(freeIds::contains);
        freeIds.forEach(id -> issueTask(DELETE, archivePath.resolve(id), null));
    }

    public void removeWatchingDir(Path dir) {
        watcher.deregister(dir);
        watchingDir2Dirs.remove(dir);
        watchingFile2Id.keySet().removeIf(path -> path.startsWith(dir));
        discardAllInactive(dir);
        cleanFreeIds();
        statusUpdated();
    }

    public void restoreRecord(Record record, Path targetDir) {
        Path from = archivePath.resolve(record.id), to = targetDir.resolve(record.original.getFileName());
        // issueTask(COPY, from, to);
        if (Files.exists(from)) try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restoreFile(Path file, Path targetDir) {
        if (!original2Records.keySet().contains(file)) return;
        restoreRecord(Collections.max(original2Records.get(file)), targetDir);
    }

    public void restoreDir(Path dir, Path targetDir) {
        original2Records.entrySet().stream().filter(entry -> entry.getKey().startsWith(dir))
                .forEach(entry -> restoreFile(entry.getKey(), targetDir.resolve(dir.relativize(entry.getKey().getParent()))));
    }

    private void handleEvents() {
        gui.agentLogPrintln("Start Handling Events");
        while (!log.isEmpty()) {
            WatcherEvent we = log.poll();
            Path entry = we.absPath;
            switch (we.type) {
                case FILE_CREATE:
                case FILE_MODIFY:
                    if (Files.exists(entry)) issueTask(UPDATE, entry, null);
                    break;
                case DIR_CREATE:
                    updateDir(entry);
                    break;
                case DELETE:
                    Path root = getRootWatchingDir(entry);
                    if (root != null) {
                        if (watchingDir2Dirs.get(root).contains(entry)) { // Is directory
                            watchingFile2Id.keySet().removeIf(file -> file.startsWith(entry));
                        } else { // Is file
                            watchingFile2Id.keySet().remove(entry);
                        }
                        /* Auto
                        discardAllInactive(root);
                        cleanFreeIds();
                        */
                    }
            }
        }
    }

    private void solveTasks() throws IOException {
        gui.agentLogPrintln("Start Solving Tasks");
        Set<Task> solved = new HashSet<>();
        for (Task t : pendingTasks) {
            if (pendingUpdate != 0 && t.type != UPDATE) continue;
            switch (t.type) {
                case UPDATE:
                    watchingFile2Id.put(t.from, Utils.getSHA256(t.from));
                    archiveFile(t.from);
                    pendingUpdate --;
                    break;
                case COPY:
                    if (Files.exists(t.from)) {
                        Files.createDirectories(t.to.getParent());
                        Files.copy(t.from, t.to, StandardCopyOption.REPLACE_EXISTING);
                    }
                    break;
                case DELETE:
                    if (Files.exists(t.from)) Files.delete(t.from);
                    break;
            }
            solved.add(t);
            gui.agentLogPrintln("Task solved: " + t.toString());
        }
        pendingTasks.removeAll(solved);
    }

    public void run() {
        try {
            gui.agentLogPrintln("Agent started at " + System.currentTimeMillis());
            while (!stopped) {
                if (!syncing) {
                    Thread.sleep(checkInterval);
                    continue;
                }
                if (log.isEmpty() && pendingTasks.isEmpty()) {
                    if (!idle) {
                        idle = true;
                        dataUpdated();
                        statusUpdated();
                        gui.agentLogPrintln("Agent fall asleep at " + System.currentTimeMillis());
                    }
                    Thread.sleep(checkInterval);
                } else {
                    gui.agentLogPrintln("Agent woke up at " + System.currentTimeMillis());
                    idle = false;
                    statusUpdated();
                    if (!log.isEmpty()) {
                        handleEvents();
                    } else if (!pendingTasks.isEmpty()) {
                        solveTasks();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void statusUpdated() {
        gui.agentLogPrintln("Updating Status Json ...");
        JsonObject obj = makeStatusJson();
        Utils.writeFile(obj.toString(), statusJsonPath);
        gui.wsServer.sendAll(Utils.makeMessage("update_status", obj));
    }

    public void dataUpdated() {
        gui.agentLogPrintln("Updating Data Json ...");
        Utils.writeFile(makeDataJson().toString(), dataJsonPath);
        gui.wsServer.sendAll(Utils.makeMessage("update_data", null));
    }

    public void setSyncing(boolean sync) {
        syncing = sync;
    }

    public void stopAgent() {
        watcher.stopWatching();
        stopped = true;
        gui.agentLogPrintln("Agent stopped at " + System.currentTimeMillis());
    }

    public JsonObject makeStatusJson() {
        JsonObject res = new JsonObject();
        res.addProperty("archive_folder", agentPath.toString());
        JsonArray arr = new JsonArray();
        for (Path dir : watchingDir2Dirs.keySet()) arr.add(dir.toString());
        res.add("watching_dirs", arr);
        res.addProperty("autoSyncing", autoSyncing);
        res.addProperty("launchOnStartup", launchOnStart);
        res.addProperty("autoDiscardOutdated", autoDiscardOutdated);
        res.addProperty("autoCleanInactive", autoCleanInactive);
        res.addProperty("idle", idle);
        res.addProperty("syncing", syncing);
        return res;
    }

    private void applyStatusJson(JsonObject obj) {
        JsonArray arr = obj.get("watching_dirs").getAsJsonArray();
        for (JsonElement e : arr) addWatchingDir(Paths.get(e.getAsString()));
        autoSyncing = obj.get("autoSyncing").getAsBoolean();
        launchOnStart = obj.get("launchOnStartup").getAsBoolean();
        autoDiscardOutdated = obj.get("autoDiscardOutdated").getAsBoolean();
        autoCleanInactive = obj.get("autoCleanInactive").getAsBoolean();
    }

    private JsonObject makeDataJson() {
        JsonObject res = new JsonObject();
        res.add("original2Records", Utils.pathMapRecordSet2Json(original2Records));
        return res;
    }

    private void applyDataJson(JsonObject obj) {
        original2Records = Utils.json2PathMapRecordSet(obj.get("original2Records").getAsJsonObject());
        original2Records.values().forEach(set -> set.forEach(record -> {
            id2Records.putIfAbsent(record.id, new HashSet<>());
            id2Records.get(record.id).add(record);
        }));
    }

    public boolean isValidWatchingDir(Path p) {
        return watchingDir2Dirs.keySet().stream().anyMatch(p::equals);
    }

    public boolean isValidFile(Path p) {
        return original2Records.keySet().contains(p);
    }

    public boolean isActiveFile(Path file) {
        return watchingFile2Id.keySet().contains(file);
    }

    private Path getRootWatchingDir(Path p) {
        return watchingDir2Dirs.keySet().stream().filter(p::startsWith).findFirst().orElse(null);
    }

    public Record getRecord(Path original, String id) {
        for (Map.Entry<Path, Set<Record>> entry : original2Records.entrySet())
            if (entry.getKey().equals(original)) {
                for (Record r : entry.getValue())
                    if (r.id.equals(id)) return r;
                return null;
            }
        return null;
    }

    public boolean isOnlyRecord(Record r) {
        return original2Records.get(r.original).size() == 1;
    }

    public JsonObject getAllRecords(Path p) {
        if (!isValidWatchingDir(p)) return null;
        JsonObject obj = new JsonObject();
        JsonArray fileObjs = new JsonArray();
        for (Path file : original2Records.keySet()) {
            if (file.startsWith(p)) {
                JsonObject fileObj = new JsonObject();
                fileObj.addProperty("path", file.toString());
                fileObj.addProperty("isActive", watchingFile2Id.keySet().contains(file));
                fileObj.add("records", Utils.recordSet2Json(original2Records.get(file)));
                fileObjs.add(fileObj);
            }
        }
        obj.add("files", fileObjs);
        return obj;
    }
}
