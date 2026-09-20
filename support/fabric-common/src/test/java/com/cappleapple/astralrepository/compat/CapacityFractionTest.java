package com.cappleapple.astralrepository.compat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapacityFractionTest {
    @Test void uncommonStackSizesPreserveFractionalCapacityUntilFinalRounding() {
        var fraction=new StacksNotSlotsCapacity.Fraction(BigInteger.valueOf(64),BigInteger.valueOf(96));
        assertEquals(BigInteger.valueOf(2),fraction.numerator());
        assertEquals(BigInteger.valueOf(3),fraction.denominator());
        assertEquals(64,fraction.ceil(96));
        assertEquals(1,fraction.ceil(1));
        assertEquals(2,fraction.ceil(3));
    }
    @Test void extremeCountsSaturateAndInvalidCostsAreRejected() {
        var fraction=new StacksNotSlotsCapacity.Fraction(BigInteger.valueOf(64),BigInteger.ONE);
        assertEquals(Long.MAX_VALUE,fraction.ceil(Long.MAX_VALUE));
        assertEquals(0,fraction.ceil(0));
        assertThrows(IllegalArgumentException.class,() -> new StacksNotSlotsCapacity.Fraction(BigInteger.ONE,BigInteger.ZERO));
    }
}