package net.rarea.lab.archive_agent.agent;

import com.sun.nio.file.ExtendedWatchEventModifier;
import net.rarea.lab.archive_agent.gui.GUI;

import java.io.*;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

class Watcher {
    private GUI gui;
    private final WatchService service;
    private final HashMap<WatchKey, Path> keys;
    private EventProcessor processor;
    Queue<WatcherEvent> log;

    /**
     * Creates a WatchService
     */
    Watcher(GUI gui) throws IOException {
        this.gui = gui;
        service = FileSystems.getDefault().newWatchService();
        keys = new HashMap<>();
        log = new ConcurrentLinkedQueue<>();
        processor = new EventProcessor();
        processor.start();
    }

    /**
     * Register the given directory with the WatchService
     */
    void register(Path dir) {
        gui.agentLogPrint("Watcher: Registering " + dir + " ... ");
        WatchEvent.Kind<?>[] kinds = {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
        WatchKey key;
        try {
            key = dir.register(service, kinds, ExtendedWatchEventModifier.FILE_TREE);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Path prev = keys.get(key);
        if (prev == null) gui.agentLogPrint("as new WatchKey ... ");
        else if (!dir.equals(prev)) gui.agentLogPrint("from previous WatchKey " + prev + " ... ");
        keys.put(key, dir);
        gui.agentLogPrintln("Done.");
    }

    /**
     * Deregister the given directory from the WatchService
     */
    void deregister(Path dir) {
        WatchKey key;
        try {
            key = dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        keys.remove(key);
    }

    void stopWatching() {
        processor.stopped = true;
    }

    private class EventProcessor extends Thread {
        boolean stopped = false;
        @SuppressWarnings("unchecked")
        <T> WatchEvent<T> cast(WatchEvent<?> event) {
            return (WatchEvent<T>)event;
        }

        EventProcessor() {
            setName("AA-EventProcessor");
        }

        /**
         * Log all events for keys queued to the watcher
         */
        public void run() {
            while (!stopped) {
                // wait for key to be signalled
                WatchKey key;
                try {
                    key = service.take();
                } catch (InterruptedException x) {
                    return;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    System.err.println("Watcher Error: Unrecognized WatchKey");
                    continue;
                }

                for (WatchEvent<?> ev: key.pollEvents()) {
                    WatchEvent<Path> event = cast(ev);
                    WatchEvent.Kind kind = event.kind();

                    if (kind == OVERFLOW) continue;

                    Path absPath = dir.resolve(event.context());
                    WatcherEvent we = null;
                    if (kind == ENTRY_DELETE) {
                        we = new WatcherEvent(EventType.DELETE, absPath);
                    } else if (Files.isDirectory(absPath)) {
                        if (kind == ENTRY_CREATE) we = new WatcherEvent(EventType.DIR_CREATE, absPath);
                    } else {
                        if (kind == ENTRY_CREATE) {
                            we = new WatcherEvent(EventType.FILE_CREATE, absPath);
                        } else if (kind == ENTRY_MODIFY) {
                            we = new WatcherEvent(EventType.FILE_MODIFY, absPath);
                        }
                    }
                    if (we != null) {
                        log.add(we);
                        gui.agentLogPrintln("Event logged: " + we.toString());
                    }
                }

                // reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    // all directories are inaccessible
                    if (keys.isEmpty()) break;
                }
            }
        }
    }
}