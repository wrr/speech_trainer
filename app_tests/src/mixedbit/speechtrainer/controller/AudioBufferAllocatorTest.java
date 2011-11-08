/**
 * This file is part of Speech Trainer.
 * Copyright (C) 2011 Jan Wrobel <wrr@mixedbit.org>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mixedbit.speechtrainer.controller;

import junit.framework.TestCase;
import mixedbit.speechtrainer.controller.AudioBufferAllocator.AudioBuffer;

public class AudioBufferAllocatorTest extends TestCase {
    private static final int BUFFER_SIZE = 100;
    private static final int NUMBER_OF_BUFFERS = 3;

    final AudioBufferAllocator allocator = new AudioBufferAllocator(NUMBER_OF_BUFFERS, BUFFER_SIZE);

    public void testBufferPoolBounded() {
        // Allocate all available buffers.
        AudioBuffer buffer;
        for (int i = 0; i < NUMBER_OF_BUFFERS; ++i) {
            buffer = allocator.allocateAudioBuffer();
            assertNotNull(buffer);
        }

        // Allocator should return null when a buffer is requested, but all
        // available buffers are already allocated.
        buffer = allocator.allocateAudioBuffer();
        assertNull(buffer);
    }

    public void testReleasedBufferBecomesAvailable() {
        // Allocate all available buffers.
        AudioBuffer buffer = null;
        for (int i = 0; i < NUMBER_OF_BUFFERS; ++i) {
            buffer = allocator.allocateAudioBuffer();
            assertNotNull(buffer);
        }

        // Release one buffer, it should become available for allocation.
        allocator.releaseAudioBuffer(buffer);

        // Allocator should return released buffer.
        buffer = allocator.allocateAudioBuffer();
        assertNotNull(buffer);
    }

    public void testReleasedBufferCanNotBeReleasedAgain() {
        final AudioBuffer buffer = allocator.allocateAudioBuffer();
        allocator.releaseAudioBuffer(buffer);
        try {
            // A buffer can not be released twice.
            allocator.releaseAudioBuffer(buffer);
            fail("Exception not thrown when an already released buffer was released again.");
        } catch (final IllegalStateException e) {
            // Expected.
        }
    }

    public void testReleasedBufferCanNotBeAccessed() {
        final AudioBuffer buffer = allocator.allocateAudioBuffer();
        allocator.releaseAudioBuffer(buffer);
        // A released buffer can not be accessed in any way.
        try {
            buffer.getAudioBufferId();
            fail("Exception not thrown when getting id of a released buffer.");
        } catch (final IllegalStateException e) {
            // Expected.
        }
        try {
            buffer.getAudioData();
            fail("Exception not thrown when getting audio data of a released buffer.");
        } catch (final IllegalStateException e) {
            // Expected.
        }
        try {
            buffer.getAudioDataLengthInShorts();
            fail("Exception not thrown when getting audio data length of a released buffer.");
        } catch (final IllegalStateException e) {
            // Expected.
        }
        try {
            buffer.getSoundLevel();
            fail("Exception not thrown when getting sound level of a released buffer.");
        } catch (final IllegalStateException e) {
            // Expected.
        }

    }

    public void testSoundLevelIncreasesWhenAmplitudeIncreases() {
        final AudioBuffer buffer1 = allocator.allocateAudioBuffer();
        buffer1.getAudioData()[0] = 100;
        buffer1.getAudioData()[1] = 100;
        buffer1.getAudioData()[2] = 100;
        buffer1.audioDataStored(3);

        final AudioBuffer buffer2 = allocator.allocateAudioBuffer();
        buffer2.getAudioData()[0] = 1000;
        buffer2.getAudioData()[1] = 1000;
        buffer2.getAudioData()[2] = 1000;
        buffer2.audioDataStored(3);

        assertTrue(buffer1.getSoundLevel() < buffer2.getSoundLevel());
    }

    public void testAudioDataLengthAndSoundLevelOfAllocatedBufferAreZero() {
        AudioBuffer buffer = null;
        for (int i = 0; i < NUMBER_OF_BUFFERS; ++i) {
            buffer = allocator.allocateAudioBuffer();
            assertEquals(0, buffer.getAudioDataLengthInShorts());
            assertEquals(0.0, buffer.getSoundLevel(), 0.001);
        }
        assertNull(allocator.allocateAudioBuffer());
        // No more buffers. Set the length of one of allocated buffers to be non
        // zero, return the buffer to the allocator and make sure that the
        // buffer length is 0 when it is allocated again.
        buffer.audioDataStored(13);
        allocator.releaseAudioBuffer(buffer);
        buffer = allocator.allocateAudioBuffer();
        assertEquals(0, buffer.getAudioDataLengthInShorts());
        assertEquals(0.0, buffer.getSoundLevel(), 0.001);
    }

    public void testAssertAllAudioBuffersAvailable() {
        // No buffers are allocated, assertAllAudioBuffersAvailable should not
        // throw an exception.
        allocator.assertAllAudioBuffersAvailable();
        allocator.allocateAudioBuffer();
        try {
            allocator.assertAllAudioBuffersAvailable();
            fail("Leaked buffer not detected.");
        } catch (final AssertionError e) {
            // Expected.
        }
    }
}
