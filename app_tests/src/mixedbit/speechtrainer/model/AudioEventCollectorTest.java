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

package mixedbit.speechtrainer.model;

import java.util.Iterator;

import junit.framework.TestCase;
import mixedbit.speechtrainer.controller.AudioEventListener;

import org.easymock.EasyMock;


public class AudioEventCollectorTest extends TestCase {

    private static final double DELTA = 0.001;
    AudioEventListener mockAudioEventListener;
    AudioEventCollector audioEventCollector;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockAudioEventListener = EasyMock.createStrictMock(AudioEventListener.class);
        audioEventCollector = new AudioEventCollector(mockAudioEventListener);
    }

    private void replayAll() {
        EasyMock.replay(mockAudioEventListener);
    }

    private void verifyAll() {
        EasyMock.verify(mockAudioEventListener);
    }

    public void testMinAndMaxRecordedSoundLevel() {
        audioEventCollector.recordingStarted();
        audioEventCollector.audioBufferRecorded(0, 156.0);
        assertEquals(156.0, audioEventCollector.getMaxSoundLevel(), DELTA);
        assertEquals(156.0, audioEventCollector.getMinSoundLevel(), DELTA);

        audioEventCollector.audioBufferRecorded(1, 172.0);
        assertEquals(172.0, audioEventCollector.getMaxSoundLevel(), DELTA);
        assertEquals(156.0, audioEventCollector.getMinSoundLevel(), DELTA);

        audioEventCollector.audioBufferRecorded(2, 122.0);
        assertEquals(172.0, audioEventCollector.getMaxSoundLevel(), DELTA);
        assertEquals(122.0, audioEventCollector.getMinSoundLevel(), DELTA);
    }

    public void testAudioEventsPropagatedToNextListener() {
        final int bufferId = 1;
        final int soundLevel = 112;
        mockAudioEventListener.recordingStarted();
        mockAudioEventListener.audioBufferRecorded(bufferId, soundLevel);
        mockAudioEventListener.recordingStopped();
        mockAudioEventListener.playingStarted();
        mockAudioEventListener.audioBufferPlayed(bufferId, soundLevel);
        mockAudioEventListener.playingStopped();
        replayAll();

        audioEventCollector.recordingStarted();
        audioEventCollector.audioBufferRecorded(bufferId, soundLevel);
        audioEventCollector.recordingStopped();
        audioEventCollector.playingStarted();
        audioEventCollector.audioBufferPlayed(bufferId, soundLevel);
        audioEventCollector.playingStopped();
        verifyAll();
    }

    public void testAudioEventIterationPerformedOverAllEventsInLIFOOrder() {
        final int eventsCount = 100;
        audioEventCollector.recordingStarted();
        for (int i = 0; i < eventsCount; i++) {
            audioEventCollector.audioBufferRecorded(i, 100 + i);
        }
        ;
        final Iterator<? extends AudioBufferInfo> it =
            audioEventCollector.getIteratorOverAudioEventsToPlot(10);
        int iteratedEventsCount = 0;
        while (it.hasNext()) {
            final AudioBufferInfo audioBufferInfo = it.next();
            iteratedEventsCount += 1;
            assertEquals(eventsCount - iteratedEventsCount, audioBufferInfo.getAudioBufferId());
            assertEquals(100 + eventsCount - iteratedEventsCount,
                    audioBufferInfo.getSoundLevel(), DELTA);
            // No buffers were played.
            assertFalse(audioBufferInfo.isPlayed());
        }
        assertEquals(eventsCount, iteratedEventsCount);
    }

    public void testResetHistory() {
        audioEventCollector.recordingStarted();
        audioEventCollector.audioBufferRecorded(0, 156.0);
        audioEventCollector.audioBufferRecorded(1, 12.0);
        assertEquals(12.0, audioEventCollector.getMinSoundLevel(), DELTA);
        assertEquals(156.0, audioEventCollector.getMaxSoundLevel(), DELTA);
        assertTrue(audioEventCollector.getIteratorOverAudioEventsToPlot(1).hasNext());

        audioEventCollector.resetHistory();
        // Make sure information about two recorded buffers is removed.
        assertEquals(Double.MAX_VALUE, audioEventCollector.getMinSoundLevel(), DELTA);
        assertEquals(0.0, audioEventCollector.getMaxSoundLevel(), DELTA);
        assertFalse(audioEventCollector.getIteratorOverAudioEventsToPlot(1).hasNext());
    }

    public void testHistorySizeRespectedOldEventsRemoved() {
        final int eventsCount = 2 * AudioEventCollector.HISTORY_SIZE;
        audioEventCollector.recordingStarted();
        for (int i = 0; i < eventsCount; i++) {
            audioEventCollector.audioBufferRecorded(i, 100 + i);
        }
        final Iterator<? extends AudioBufferInfo> it =
            audioEventCollector.getIteratorOverAudioEventsToPlot(10);

        int iteratedEventsCount = 0;
        while (it.hasNext()) {
            final AudioBufferInfo audioBufferInfo = it.next();
            iteratedEventsCount += 1;
            assertEquals(eventsCount - iteratedEventsCount, audioBufferInfo.getAudioBufferId());
            assertEquals(100 + eventsCount - iteratedEventsCount,
                    audioBufferInfo.getSoundLevel(), DELTA);
            // No buffers were played.
            assertFalse(audioBufferInfo.isPlayed());
        }
        assertEquals(AudioEventCollector.HISTORY_SIZE, iteratedEventsCount);
    }

    public void testIteratorRemainsValidUntilTheNextCallToGetIterator() {
        final int eventsCount = AudioEventCollector.HISTORY_SIZE;
        final double soundLevel = 51.5;
        audioEventCollector.recordingStarted();
        for (int i = 0; i < eventsCount; i++) {
            audioEventCollector.audioBufferRecorded(i, soundLevel);
        }
        Iterator<? extends AudioBufferInfo> it = audioEventCollector
        .getIteratorOverAudioEventsToPlot(10);

        // New events cause HISTORY_SIZE to be exceeded, but the iterator over
        // audio events should remain valid until the next call to
        // getIteratorOverAudioEventsToDisplay().
        for (int i = 0; i < eventsCount; i++) {
            audioEventCollector.audioBufferRecorded(i + eventsCount, soundLevel);
        }

        int iteratedEventsCount = 0;
        while (it.hasNext()) {
            final AudioBufferInfo audioBufferInfo = it.next();
            iteratedEventsCount += 1;
            // Make sure iterator goes through old events.
            assertEquals(eventsCount - iteratedEventsCount, audioBufferInfo.getAudioBufferId());
        }
        assertEquals(AudioEventCollector.HISTORY_SIZE, eventsCount);

        // Get the iterator again, this time old events should be removed.
        it = audioEventCollector.getIteratorOverAudioEventsToPlot(10);
        iteratedEventsCount = 0;
        while (it.hasNext()) {
            final AudioBufferInfo audioBufferInfo = it.next();
            iteratedEventsCount += 1;
            assertEquals(2 * eventsCount - iteratedEventsCount, audioBufferInfo.getAudioBufferId());
        }
        assertEquals(AudioEventCollector.HISTORY_SIZE, eventsCount);
    }

    public void testRecentlyPlayedBuffersMarked() {
        final double soundLevel = 12.0;
        audioEventCollector.recordingStarted();
        audioEventCollector.audioBufferRecorded(0, soundLevel);
        audioEventCollector.audioBufferRecorded(1, soundLevel);
        audioEventCollector.audioBufferRecorded(2, soundLevel);
        audioEventCollector.audioBufferRecorded(3, soundLevel);
        audioEventCollector.recordingStopped();
        audioEventCollector.playingStarted();
        audioEventCollector.audioBufferPlayed(1, soundLevel);
        audioEventCollector.audioBufferPlayed(2, soundLevel);
        audioEventCollector.playingStopped();

        Iterator<? extends AudioBufferInfo> it = audioEventCollector
        .getIteratorOverAudioEventsToPlot(10);
        AudioBufferInfo audioBufferInfo = it.next();
        assertEquals(3, audioBufferInfo.getAudioBufferId());
        assertFalse(audioBufferInfo.isPlayed());

        audioBufferInfo = it.next();
        assertEquals(2, audioBufferInfo.getAudioBufferId());
        assertTrue(audioBufferInfo.isPlayed());

        audioBufferInfo = it.next();
        assertEquals(1, audioBufferInfo.getAudioBufferId());
        assertTrue(audioBufferInfo.isPlayed());

        audioBufferInfo = it.next();
        assertEquals(0, audioBufferInfo.getAudioBufferId());
        assertFalse(audioBufferInfo.isPlayed());

        assertFalse(it.hasNext());

        audioEventCollector.playingStarted();
        it = audioEventCollector.getIteratorOverAudioEventsToPlot(10);
        assertFalse(it.next().isPlayed());
        assertFalse(it.next().isPlayed());
        assertFalse(it.next().isPlayed());
        assertFalse(it.next().isPlayed());
        assertFalse(it.hasNext());
    }

    public void testIteratorCenteredOnLastPlayedBufferWhilePlaying() {
        final double soundLevel = 12.0;
        final int plotWidth = 3;
        audioEventCollector.recordingStarted();
        audioEventCollector.audioBufferRecorded(0, soundLevel);
        audioEventCollector.audioBufferRecorded(1, soundLevel);
        audioEventCollector.audioBufferRecorded(2, soundLevel);
        audioEventCollector.audioBufferRecorded(3, soundLevel);
        audioEventCollector.audioBufferRecorded(4, soundLevel);
        audioEventCollector.recordingStopped();
        audioEventCollector.playingStarted();
        audioEventCollector.audioBufferPlayed(1, soundLevel);

        Iterator<? extends AudioBufferInfo> it =
            audioEventCollector.getIteratorOverAudioEventsToPlot(plotWidth);
        AudioBufferInfo audioBufferInfo = it.next();
        // Last played buffer with id 1 should be at the center of the plot.
        // The plot has a width of 3, this means the iterator should point at
        // the buffer with id 2.
        assertEquals(2, audioBufferInfo.getAudioBufferId());
        assertFalse(audioBufferInfo.isPlayed());
        assertEquals(1, it.next().getAudioBufferId());

        audioEventCollector.audioBufferPlayed(2, soundLevel);
        // The last played buffer has id 2, so the iterator should point at 3.
        it = audioEventCollector.getIteratorOverAudioEventsToPlot(3);
        audioBufferInfo = it.next();
        assertEquals(3, audioBufferInfo.getAudioBufferId());
        assertFalse(audioBufferInfo.isPlayed());

        audioEventCollector.playingStopped();
        audioEventCollector.recordingStarted();

        // Recording started, the iterator should point at the last recorded
        // buffer (the one with id 4).
        it = audioEventCollector.getIteratorOverAudioEventsToPlot(3);
        audioBufferInfo = it.next();
        assertEquals(4, audioBufferInfo.getAudioBufferId());
        assertFalse(audioBufferInfo.isPlayed());
    }
}
