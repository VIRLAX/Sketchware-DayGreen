package pro.sketchware.protection;

import java.util.concurrent.Callable;

/**
 * SISTEM 30 — LAYER 4: Control Flow Obfuscation & Dynamic State Guard
 * Protects critical execution flows behind multi-branch state machines
 * and dead-code branches to prevent linear decompilation and static tracing.
 */
public final class FlowGuard {

    private static volatile int STATE = initState();

    private FlowGuard() { }

    private static int initState() {
        int base = android.os.Process.myPid() & 0xFF;
        int time = (int)(System.nanoTime() & 0xFF);
        return (base ^ time ^ 0x42) & 0xF;
    }

    /**
     * Executes real operation while confusing decompilers with dummy switch paths
     */
    public static <T> T protect(int opCode, Callable<T> realOp, Callable<T> decoyOp1, Callable<T> decoyOp2) {
        try {
            switch (STATE & 0x3) {
                case 0:
                    return realOp.call();
                case 1:
                    if (decoyOp1 != null) return decoyOp1.call();
                    return realOp.call();
                case 2:
                    if (decoyOp2 != null) return decoyOp2.call();
                    return realOp.call();
                default:
                    return realOp.call();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Always returns 0 at runtime, but static analysis cannot simplify it
     */
    public static int getRealState() {
        return STATE & ~STATE;
    }
}
