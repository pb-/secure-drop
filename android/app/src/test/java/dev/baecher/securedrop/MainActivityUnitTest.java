package dev.baecher.securedrop;

import org.junit.Test;

import java.lang.reflect.Array;
import java.util.Arrays;

import static org.junit.Assert.*;

public class MainActivityUnitTest {
    @Test
    public void writing48BitIntWorks() {
        byte[] buffer = new byte[6];
        MainActivity.write48BitInt(511, buffer, 0);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 1, -1}, buffer);
    }

    @Test
    public void writingIvWorks() {
        byte[] buffer = MainActivity.computeIv(2,1);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1}, buffer);
    }

    @Test
    public void chunksComputationWorks() {
        assertEquals(1, MainActivity.getChunks(1000, 1));
        assertEquals(1, MainActivity.getChunks(1000, 1000));
        assertEquals(2, MainActivity.getChunks(1000, 1001));
    }

}