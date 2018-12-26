package net.rarea.lab.archive_agent.agent;

import java.nio.file.Path;

public class Task {
    private final static String[] taskTypes = {"UPDATE", "COPY", "DELETE"};
    private final static int UPDATE = 0, COPY = 1, DELETE = 2;
    int type;
    Path from, to;
    Task(int type, Path from, Path to) {
        this.type = type;
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return taskTypes[type] + " " + from + (type == COPY ? " -> " + to : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Task)) return false;
        Task t = (Task) obj;
        return type == t.type && from.equals(t.from) && (to == null && t.to == null || to !=null && t.to != null && to.equals(t.to));
    }

    boolean overridePrevDelete(Task t) {
        if (t.type != DELETE) return false;
        if (type == COPY) {
            return to.equals(t.from);
        } else if (type == UPDATE) {
            return from.equals(t.from);
        }
        return false;
    }
}