package org.dynmap.fabric_26_1.access;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import org.dynmap.Log;

/**
 * Version-tolerant accessors for {@link ServerboundSignUpdatePacket}.
 * <p>
 * The packet API changed between the Minecraft versions covered by this module:
 * <ul>
 *   <li>26.1 - 26.2: {@code getPos()} and {@code isFrontText()}</li>
 *   <li>26.3+: record accessors {@code pos()} and {@code slot()} ({@code SignTextSlot.FRONT} / {@code BACK})</li>
 * </ul>
 * Resolving the accessors by name at runtime keeps a single jar working across all of them.
 * Minecraft ships unobfuscated since 26.1, so the names are stable at runtime.
 */
public final class SignUpdatePacketAccess {
    private static final Method POS = find("getPos", "pos");
    private static final Method FRONT = find("isFrontText", "slot");

    private SignUpdatePacketAccess() {
    }

    /**
     * @return the sign position, or {@code null} if no known accessor exists on this Minecraft version
     */
    public static BlockPos pos(ServerboundSignUpdatePacket packet) {
        Object value = invoke(POS, packet);
        return (value instanceof BlockPos) ? (BlockPos) value : null;
    }

    /**
     * @return {@code true} when the packet targets the front text (default when unknown)
     */
    public static boolean isFrontText(ServerboundSignUpdatePacket packet) {
        Object value = invoke(FRONT, packet);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Enum) {
            return "FRONT".equals(((Enum<?>) value).name());
        }
        return true;
    }

    private static Method find(String... names) {
        for (String name : names) {
            try {
                return ServerboundSignUpdatePacket.class.getMethod(name);
            } catch (NoSuchMethodException e) {
                // try next candidate
            }
        }
        Log.warning("Dynmap: none of " + String.join("/", names)
                + " found on ServerboundSignUpdatePacket - sign change events will be disabled");
        return null;
    }

    private static Object invoke(Method method, ServerboundSignUpdatePacket packet) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(packet);
        } catch (ReflectiveOperationException e) {
            Log.severe("Dynmap: failed to read sign update packet", e);
            return null;
        }
    }
}
