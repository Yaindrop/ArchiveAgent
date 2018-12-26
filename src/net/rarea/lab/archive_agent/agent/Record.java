package net.rarea.lab.archive_agent.agent;

import java.nio.file.Path;

public class Record implements Comparable<Record> {
    public Path original;
    public String id;
    public long time;

    public Record(Path original, String id) {
        this.original = original;
        this.id = id;
        time = System.currentTimeMillis();
    }

    @Override
    public int compareTo(Record r) {
        return (int) (this.time - r.time);
    }
}