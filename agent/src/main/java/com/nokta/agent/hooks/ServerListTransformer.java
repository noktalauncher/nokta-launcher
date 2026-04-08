package com.nokta.agent.hooks;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Sunucu listesi transformer.
 * Sadece net/minecraft altindaki sinifları tarar.
 */
public class ServerListTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> cls, ProtectionDomain pd, byte[] bytes) {
        if (className == null || bytes == null) return null;
        if (!className.startsWith("net/minecraft")) return null;
        return null; // Simdilik pasif
    }
}
