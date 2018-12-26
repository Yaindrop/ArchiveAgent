package net.rarea.lab.archive_agent;

import com.google.gson.*;
import net.rarea.lab.archive_agent.agent.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Utils {
    public static String getSHA256(Path p) {
        if (!Files.exists(p) || Files.isDirectory(p)) return null;
        try {
            if (Files.size(p) == 0) return ".emptyfile";
            FileInputStream fis = new FileInputStream(p.toFile());
            String res = DigestUtils.sha256Hex(fis);
            fis.close();
            return res;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void openLocalDir(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JsonObject readAsJson(Path file) {
        if (!Files.exists(file)) return null;
        try {
            return new JsonParser().parse(new FileReader(file.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeFile(String data, Path dest) {
        try {
            ArrayList<String> line = new ArrayList<>();
            line.add(data);
            Files.write(dest, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JsonObject makeMessage(String type, JsonElement content) {
        JsonObject res = new JsonObject();
        res.add("type", new JsonPrimitive(type));
        res.add("content", content);
        return res;
    }

    public static JsonElement record2Json(Record r) {
        JsonObject res = new JsonObject();
        res.addProperty("time", r.time);
        res.addProperty("id", r.id);
        res.addProperty("original", r.original.toString());
        return res;
    }

    public static Record json2Record(JsonObject obj) {
        Record res = new Record(Paths.get(obj.get("original").getAsString()), obj.get("id").getAsString());
        res.time = obj.get("time").getAsLong();
        return res;
    }

    public static JsonElement recordSet2Json(Set<Record> set) {
        ArrayList<Record> list = new ArrayList<>(set);
        Collections.sort(list);
        JsonArray res = new JsonArray();
        for (Record r : list) res.add(record2Json(r));
        return res;
    }

    public static Set<Record> json2recordSet(JsonArray arr) {
        Set<Record> res = new HashSet<>();
        for (JsonElement ele : arr) res.add(json2Record(ele.getAsJsonObject()));
        return res;
    }

    public static JsonElement pathMapRecordSet2Json(Map<Path, Set<Record>> map) {
        JsonObject res = new JsonObject();
        for (Map.Entry<Path, Set<Record>> e : map.entrySet()) res.add(e.getKey().toString(), recordSet2Json(e.getValue()));
        return res;
    }

    public static Map<Path, Set<Record>> json2PathMapRecordSet(JsonObject obj) {
        Map<Path, Set<Record>> res = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) res.put(Paths.get(entry.getKey()), json2recordSet(entry.getValue().getAsJsonArray()));
        return res;
    }
}
