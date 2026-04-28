package com.wig3003.photoapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BufferedImageTest {

    // -------------------------------------------------------------------------
    // Constructor / getters
    // -------------------------------------------------------------------------

    @Test
    void constructor_storesAllFieldsCorrectly() {
        byte[] data = {10, 20, 30, 40, 50, 60};
        BufferedImage bi = new BufferedImage(2, 1, 3, data);

        assertEquals(2, bi.getWidth());
        assertEquals(1, bi.getHeight());
        assertEquals(3, bi.getChannels());
        assertArrayEquals(data, bi.getData());
    }

    @Test
    void constructor_nullData_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BufferedImage(1, 1, 3, null));
    }

    // -------------------------------------------------------------------------
    // Immutability — getData() must return a copy, not the internal array
    // -------------------------------------------------------------------------

    @Test
    void getData_returnsCopy_notTheSameArrayReference() {
        byte[] original = {1, 2, 3};
        BufferedImage bi = new BufferedImage(1, 1, 3, original);

        // Two successive calls should return equal but distinct arrays
        assertNotSame(bi.getData(), bi.getData());
    }

    @Test
    void getData_mutatingReturnedArray_doesNotAffectStoredData() {
        byte[] original = {10, 20, 30};
        BufferedImage bi = new BufferedImage(1, 1, 3, original);

        byte[] copy = bi.getData();
        copy[0] = (byte) 99; // mutate the returned copy

        // The stored data must be unchanged
        assertEquals(10, bi.getData()[0]);
    }

    @Test
    void constructor_mutatingSourceArray_doesNotAffectStoredData() {
        byte[] source = {5, 6, 7};
        BufferedImage bi = new BufferedImage(1, 1, 3, source);

        source[0] = (byte) 99; // mutate the array that was passed to the constructor

        // The internal copy must be unaffected
        assertEquals(5, bi.getData()[0]);
    }
}
