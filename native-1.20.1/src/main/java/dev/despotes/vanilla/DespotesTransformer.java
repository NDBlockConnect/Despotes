package dev.despotes.vanilla;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * v26.2-Alpha.3: ASM dynamic instrumentation for the native loader line.
 *
 * <p>Two injection points, chosen because they exist under identical names and
 * descriptors on every supported Minecraft version (1.20.1 → 26.2, official mappings):
 *
 * <ol>
 *   <li>{@code net/minecraft/client/Minecraft.tick()V} — an exit hook calls
 *       {@code DespotesHooks.onClientTick()}, driving the control core straight from the
 *       game's own tick loop instead of the legacy 20 Hz scheduled pump (lower latency,
 *       exact 1:1 tick alignment, no extra scheduler thread).</li>
 *   <li>{@code ClientPacketListener.handleSystemChat / handlePlayerChat} — entry hooks
 *       publish {@code system}/{@code chat} events onto the event bus, closing the
 *       perception gap the native line had versus the fabric line.</li>
 * </ol>
 *
 * <p>The transformer is registered in premain before any game class loads; the pump in
 * {@link DespotesAgent} stays as a fallback and suspends itself once the tick hook fires.
 * Transformations are defensive: any failure returns the original bytes untouched.
 */
public final class DespotesTransformer implements ClassFileTransformer {

    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String CONNECTION = "net/minecraft/client/multiplayer/ClientPacketListener";
    private static final String SYSTEM_CHAT_PACKET =
            "(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V";
    private static final String PLAYER_CHAT_PACKET =
            "(Lnet/minecraft/network/protocol/game/ClientboundPlayerChatPacket;)V";
    private static final String DISGUISED_CHAT_PACKET =
            "(Lnet/minecraft/network/protocol/game/ClientboundDisguisedChatPacket;)V";

    private volatile boolean minecraftTransformed;
    private volatile boolean chatTransformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        try {
            if (MINECRAFT.equals(className)) {
                System.out.println("[Despotes] ASM: instrumenting Minecraft.tick ...");
                byte[] out = instrumentTick(classfileBuffer);
                minecraftTransformed = out != null;
                System.out.println("[Despotes] ASM: Minecraft transform " + (out != null ? "OK" : "SKIPPED"));
                return out;
            }
            if (CONNECTION.equals(className)) {
                System.out.println("[Despotes] ASM: instrumenting ClientPacketListener chat handlers ...");
                byte[] out = instrumentChat(classfileBuffer);
                chatTransformed = out != null;
                System.out.println("[Despotes] ASM: chat transform " + (out != null ? "OK" : "SKIPPED"));
                return out;
            }
        } catch (Throwable t) {
            System.out.println("[Despotes] instrumentation failed for " + className + ": " + t);
        }
        return null; // never break the game: null keeps the original bytes
    }

    /** Append {@code DespotesHooks.onClientTick()} at every normal exit of tick(). */
    private byte[] instrumentTick(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv != null && "tick".equals(name) && "()V".equals(descriptor)) {
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode != ATHROW) {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/despotes/vanilla/DespotesHooks",
                                        "onClientTick", "()V", false);
                            }
                        }
                    };
                }
                return mv;
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    /** Insert entry hooks into the chat packet handlers. */
    private byte[] instrumentChat(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null) {
                    return null;
                }
                String hook = switch (descriptor) {
                    case SYSTEM_CHAT_PACKET -> name.equals("handleSystemChat") ? "onSystemChat" : null;
                    case PLAYER_CHAT_PACKET -> name.equals("handlePlayerChat") ? "onPlayerChat" : null;
                    case DISGUISED_CHAT_PACKET -> name.equals("handleDisguisedChat") ? "onDisguisedChat" : null;
                    default -> null;
                };
                if (hook == null) {
                    return mv;
                }
                return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                    @Override
                    protected void onMethodEnter() {
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "dev/despotes/vanilla/DespotesHooks",
                                hook, "(Ljava/lang/Object;)V", false);
                    }
                };
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    public boolean minecraftTransformed() {
        return minecraftTransformed;
    }

    public boolean chatTransformed() {
        return chatTransformed;
    }
}
