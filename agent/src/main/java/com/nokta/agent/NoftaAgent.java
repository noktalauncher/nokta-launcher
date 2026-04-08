package com.nokta.agent;

import com.nokta.agent.hooks.ServerConnectionHook;
import com.nokta.agent.hooks.ServerListTransformer;
import java.lang.instrument.Instrumentation;

public class NoftaAgent {
    public static void premain(String args, Instrumentation inst) { start(args, inst); }
    public static void agentmain(String args, Instrumentation inst) { start(args, inst); }

    private static void start(String args, Instrumentation inst) {
        System.out.println("[NoftaAgent] Baslatiliyor...");
        inst.addTransformer(new ServerListTransformer(), true);
        Thread t = new Thread(new ServerConnectionHook(), "nokta-server-detect");
        t.setDaemon(true);
        t.start();
        System.out.println("[NoftaAgent] Hazir!");
    }
}
