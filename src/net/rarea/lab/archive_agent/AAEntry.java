package net.rarea.lab.archive_agent;

import net.rarea.lab.archive_agent.gui.*;

import java.io.IOException;

public class AAEntry {
    public static void main(String[] args) {
        try {
            System.setProperty("file.encoding", "UTF-8");
            new GUI();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
