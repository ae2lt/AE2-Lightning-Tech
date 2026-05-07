package com.moakiee.ae2lt.me.cell;

/**
 * 126-bit unsigned arithmetic using two non-negative Java longs (63 + 63 bits).
 * <p>
 * Representation: {@code value = hi * 2^63 + lo} where both {@code hi} and {@code lo}
 * are in {@code [0, Long.MAX_VALUE]}.
 * <p>
 * By sacrificing 2 bits compared to full 128-bit unsigned arithmetic, all comparisons
 * and divisions use plain Java signed operators — zero {@code Long.xxxUnsigned} calls.
 */
public final class DualLong126 {

    private DualLong126() {}

    /*
     * add / sub are intentionally NOT provided as methods.
     * Callers inline the 63-bit pattern directly for zero-overhead:
     *
     *   lo += amount;                          // or lo -= amount
     *   if (lo < 0) { lo &= Long.MAX_VALUE; hi++; }   // carry / borrow
     *
     * Java overflow past Long.MAX_VALUE sets bit 63 (sign bit),
     * so (lo < 0) detects carry; (& Long.MAX_VALUE) extracts the lower 63 bits.
     */

    /** {@code (hi, lo) >= amount}?  (amount is a positive long) */
    public static boolean geq(long hi, long lo, long amount) {
        return hi > 0 || lo >= amount;
    }

    /** Clamp a 126-bit value to {@code Long.MAX_VALUE} for the AE2 API. */
    public static long cap(long hi, long lo) {
        return hi > 0 ? Long.MAX_VALUE : lo;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Cold-path helpers  (load / rebuild only)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * {@code (hi × 2^63 + lo) mod d}.
     * <p>
     * All operands non-negative, uses plain {@code %}.
     *
     * @param d a positive int (e.g. {@code AEKeyType.getAmountPerByte()})
     */
    public static long mod126(long hi, long lo, int d) {
        if (d == 1) return 0;
        long pow2_63_mod_d = (Long.MAX_VALUE % d + 1) % d; // 2^63 mod d
        return ((hi % d * pow2_63_mod_d) % d + lo % d) % d;
    }

    /**
     * {@code ceil((hi × 2^63 + lo) / d)}.
     * <p>
     * Result is written as 126-bit to {@code out[0]} (hi) and {@code out[1]} (lo).
     * Uses schoolbook long-division on 32-bit digits — safe for all valid inputs.
     *
     * @param d a positive int
     */
    public static void ceilDiv126(long hi, long lo, int d, long[] out) {
        if (d == 1) {
            out[0] = hi;
            out[1] = lo;
            return;
        }

        if (hi == 0) {
            out[0] = 0;
            long q = lo / d;
            out[1] = (lo % d == 0) ? q : q + 1;
            return;
        }

        // Represent the 126-bit value (hi*2^63 + lo) as four 32-bit digits:
        //   bit 125..96 → c3  (≤ 30 bits)
        //   bit 95..64  → c2  (32 bits)
        //   bit 63..32  → c1  (32 bits)
        //   bit 31..0   → c0  (32 bits)
        long mask32 = 0xFFFFFFFFL;
        long c3 = hi >>> 33;
        long c2 = (hi >>> 1) & mask32;
        long c1 = ((hi & 1L) << 31) | (lo >>> 32);
        long c0 = lo & mask32;

        // Schoolbook long division by d (each intermediate value < 2^63, always positive)
        long rem, q3, q2, q1, q0;

        rem = c3;
        q3 = rem / d;
        rem = rem % d;

        rem = (rem << 32) | c2;
        q2 = rem / d;
        rem = rem % d;

        rem = (rem << 32) | c1;
        q1 = rem / d;
        rem = rem % d;

        rem = (rem << 32) | c0;
        q0 = rem / d;
        rem = rem % d;

        // Reassemble quotient from four 32-bit digits into (hi, lo) in 63+63 format.
        // quotient bits: q3 at [96..127], q2 at [64..95], q1 at [32..63], q0 at [0..31]
        long low64 = (q1 << 32) | q0;
        long high62 = (q3 << 32) | q2;

        out[1] = low64 & Long.MAX_VALUE;              // lower 63 bits
        out[0] = (high62 << 1) | (low64 >>> 63);      // upper 63 bits

        if (rem > 0) {
            if (out[1] == Long.MAX_VALUE) {
                out[1] = 0;
                out[0]++;
            } else {
                out[1]++;
            }
        }
    }
}
