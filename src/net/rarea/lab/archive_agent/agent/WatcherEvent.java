package net.rarea.lab.archive_agent.agent;

import java.nio.file.*;

enum EventType {
    FILE_CREATE, FILE_MODIFY, DIR_CREATE, DELETE
}

public class WatcherEvent {
    public EventType type;
    public Path absPath;

    WatcherEvent(EventType t, Path p) {
        type = t;
        absPath = p;
    }

    @Override
    public String toString() {
        return type + " @ " + absPath;
    }
}