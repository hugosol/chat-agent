package com.hugosol.chatagent.flashcard;

/**
 * Alea PRNG — port of Johannes Baagøe's algorithm, matching ts-fsrs/py-fsrs.
 * <p>
 * Test vectors from ts-fsrs (alea.test.ts):
 * <pre>
 *  seed(12345) → [0.27138191112317145, 0.19615925149992108, 0.6810678059700876]
 *  seed(1) state equals seed(1) state
 * </pre>
 */
class AleaPrng {

    private static final double ARC4_CONST = 2.3283064365386963E-10;

    private double s0;
    private double s1;
    private double s2;
    private double c;

    AleaPrng(long seed) {
        this(String.valueOf(seed));
    }

    AleaPrng(String seed) {
        Mash m = new Mash();
        this.c = 1;
        this.s0 = m.mash(" ");
        this.s1 = m.mash(" ");
        this.s2 = m.mash(" ");
        this.s0 -= m.mash(seed);
        if (this.s0 < 0) this.s0 += 1;
        this.s1 -= m.mash(seed);
        if (this.s1 < 0) this.s1 += 1;
        this.s2 -= m.mash(seed);
        if (this.s2 < 0) this.s2 += 1;
    }

    double next() {
        double t = 2091639 * s0 + c * ARC4_CONST;
        s0 = s1;
        s1 = s2;
        c = (int) (long) t;
        s2 = t - c;
        return s2;
    }

    private static final class Mash {
        long n = 0xefc8249dL;

        double mash(String data) {
            for (int i = 0; i < data.length(); i++) {
                n += data.charAt(i);
                double h = 0.02519603282416938 * n;
                n = (long) h & 0xFFFFFFFFL;
                h -= n;
                h *= n & 0xFFFFFFFFL;
                n = (long) h & 0xFFFFFFFFL;
                h -= n;
                n += (long) (h * 0x100000000L);
            }
            return (n & 0xFFFFFFFFL) * ARC4_CONST;
        }
    }
}
