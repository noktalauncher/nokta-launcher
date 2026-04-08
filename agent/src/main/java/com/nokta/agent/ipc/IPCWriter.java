package com.nokta.agent.ipc;

import java.io.*;
import java.nio.file.*;

public class IPCWriter {
    private static final String BASE;
    static {
        BASE = System.getProperty("user.home")
             + File.separator + ".nokta-launcher" + File.separator;
        new File(BASE).mkdirs();
    }
    public static void writeServer(String addr) {
        write("server_info.json", "{\"server\":\"" + esc(addr) + "\"}");
    }
    public static void write(String name, String json) {
        try {
            Path p = Paths.get(BASE + name);
            Files.write(p, json.getBytes("UTF-8"),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
