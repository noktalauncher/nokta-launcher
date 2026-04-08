package com.nokta.agent.hooks;

import com.nokta.agent.ipc.IPCWriter;
import java.lang.reflect.*;
import java.net.SocketAddress;

public class ServerConnectionHook implements Runnable {
    private String lastServer = "__init__";

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(3000);
                String server = detect();
                if (!server.equals(lastServer)) {
                    lastServer = server;
                    IPCWriter.writeServer(server);
                    System.out.println("[NoftaAgent] Sunucu: " + server);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {}
        }
    }

    private String detect() {
        try {
            Object mc = getMC();
            if (mc == null) return "";
            Object conn = findField(mc, "connection","clientPacketListener","networkHandler","f_91761_");
            if (conn == null) return "";
            Object netConn = findField(conn, "connection","f_11588_","netManager");
            if (netConn == null) return "";
            SocketAddress addr = (SocketAddress) callMethod(netConn, "getRemoteAddress");
            if (addr == null) return "";
            String s = addr.toString();
            if (s.contains("127.0.0.1") || s.contains("localhost")) return "Singleplayer";
            return s.replaceAll(".*/","").replaceAll(":.*","").trim();
        } catch (Exception e) { return ""; }
    }

    private Object getMC() {
        for (String cn : new String[]{
                "net.minecraft.client.Minecraft","ave","bib","bqp"}) {
            try {
                Class<?> cls = Class.forName(cn);
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v != null && v.getClass() == cls) return v;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Object findField(Object obj, String... names) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields())
                for (String n : names)
                    if (f.getName().equals(n)) {
                        try { f.setAccessible(true); return f.get(obj); }
                        catch (Exception ignored) {}
                    }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private Object callMethod(Object obj, String name) {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            for (Method m : cls.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    try { m.setAccessible(true); return m.invoke(obj); }
                    catch (Exception ignored) {}
                }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
