package com.hugosol.chatagent.flashcard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AleaPrngTest {

    @Test
    void sameSeed_producesSameSequence() {
        AleaPrng prng1 = new AleaPrng(1L);
        AleaPrng prng2 = new AleaPrng(1L);

        for (int i = 0; i < 100; i++) {
            assertThat(prng1.next()).isEqualTo(prng2.next());
        }
    }

    @Test
    void differentSeeds_produceDifferentSequence() {
        AleaPrng prng1 = new AleaPrng(1L);
        AleaPrng prng2 = new AleaPrng(42L);

        boolean allSame = true;
        for (int i = 0; i < 100; i++) {
            if (prng1.next() != prng2.next()) {
                allSame = false;
                break;
            }
        }
        assertThat(allSame).isFalse();
    }

    @Test
    void valuesInRange_zeroToOne() {
        AleaPrng prng = new AleaPrng(42L);
        for (int i = 0; i < 1000; i++) {
            double v = prng.next();
            assertThat(v).isGreaterThanOrEqualTo(0.0);
            assertThat(v).isLessThan(1.0);
        }
    }
}
