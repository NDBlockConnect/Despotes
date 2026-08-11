package dev.despotes.common.perf;

import com.google.gson.JsonObject;

/**
 * v26.2-Alpha.6: rolling latency statistics for the control channel.
 *
 * <p>Every executed command contributes two samples:
 * <ul>
 *   <li><b>waited</b> — time the command spent queued before the client thread picked it
 *       up (queue contention / tick budget saturation).</li>
 *   <li><b>exec</b> — time the action itself took on the client thread.</li>
 * </ul>
 *
 * Samples live in a fixed-size circular buffer (no allocation after warm-up); the summary
 * is computed on demand for {@code /status}. All arithmetic uses microseconds internally
 * and reports milliseconds with one decimal.
 */
public final class LatencyStats {

    private static final int CAPACITY = 256;

    private final long[] waitedUs = new long[CAPACITY];
    private final long[] execUs = new long[CAPACITY];
    private int write;
    private long count;

    /** Record one command's queue wait and execution time (microseconds). Client thread. */
    public synchronized void record(long waitedMicros, long execMicros) {
        waitedUs[write] = waitedMicros;
        execUs[write] = execMicros;
        write = (write + 1) % CAPACITY;
        count++;
    }

    /** Snapshot for /status: count, avg/p95/max for waited and exec (milliseconds). */
    public synchronized JsonObject snapshot() {
        JsonObject o = new JsonObject();
        int n = (int) Math.min(count, CAPACITY);
        o.addProperty("samples", count);
        o.addProperty("window", n);
        if (n == 0) {
            return o;
        }
        long[] w = copy(waitedUs, n);
        long[] x = copy(execUs, n);
        java.util.Arrays.sort(w);
        java.util.Arrays.sort(x);
        o.add("waitedMs", block(w));
        o.add("execMs", block(x));
        return o;
    }

    private static long[] copy(long[] src, int n) {
        long[] out = new long[n];
        System.arraycopy(src, 0, out, 0, n);
        return out;
    }

    private static JsonObject block(long[] sorted) {
        JsonObject b = new JsonObject();
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        b.addProperty("avg", round1(sum / (double) sorted.length / 1000.0));
        b.addProperty("p95", round1(sorted[(int) (sorted.length * 0.95)] / 1000.0));
        b.addProperty("max", round1(sorted[sorted.length - 1] / 1000.0));
        return b;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
