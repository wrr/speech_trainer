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
import java.util.LinkedList;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.TreeSet;

import mixedbit.speechtrainer.Assertions;
import mixedbit.speechtrainer.SpeechTrainerConfig;
import mixedbit.speechtrainer.controller.AudioEventListener;

/**
 * The AudioEventCollector is separated from a GUI component ( udioEventView) to
 * simplify testing. The non-trivial logic is encapsulated in the
 * AudioEventCollector and provided to the GUI via AudioHistoryProvider
 * interface. This minimizes the GUI component responsibilities.
 */
public class AudioEventCollector implements AudioEventListener, AudioEventHistory {

    private class AudioBufferInfoImpl implements AudioBufferInfo, Comparable<AudioBufferInfoImpl> {
        private final int audioBufferId;
        private final double soundLevel;

        private AudioBufferInfoImpl(int audioBufferId, double soundLevel) {
            this.audioBufferId = audioBufferId;
            this.soundLevel = soundLevel;
        }

        @Override
        public int getAudioBufferId() {
            return audioBufferId;
        }

        @Override
        public double getSoundLevel() {
            return soundLevel;
        }

        @Override
        public boolean isPlayed() {
            synchronized (AudioEventCollector.this) {
                return firstBufferPlayed != null && firstBufferPlayed.compareTo(this) <= 0
                && lastBufferPlayed.compareTo(this) >= 0;
            }
        }

        /**
         * The largest buffer is the one recorded last.
         *
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(AudioBufferInfoImpl another) {
            if (another == null || this.audioBufferId < another.audioBufferId) {
                return -1;
            }
            if (this.audioBufferId > another.audioBufferId) {
                return 1;
            }
            return 0;
        }
    }

    // How many most recent AudioBufferInfo to keep.
    public static final int HISTORY_SIZE = SpeechTrainerConfig.NUMBER_OF_AUDIO_BUFFERS;
    // A chained listener to which all audio events are also passed (In
    // production code this is the GUI component that displays audio events).
    private final AudioEventListener nextListener;
    private final NavigableSet<AudioBufferInfoImpl> recordedBuffers = new TreeSet<AudioBufferInfoImpl>();
    // Recently recorded buffers are not added directly to recordedBuffers in
    // order not to invalidate iterator that can be in use by the GUI.
    private final Queue<AudioBufferInfoImpl> recentlyRecordedBuffers = new LinkedList<AudioBufferInfoImpl>();
    private double maxSoundLevel = 0.0;
    private double minSoundLevel = Double.MAX_VALUE;
    private AudioBufferInfoImpl firstBufferPlayed = null;
    private AudioBufferInfoImpl lastBufferPlayed = null;

    /**
     * @param nextListener
     *            A chained listener to which all audio events are passed.
     */
    public AudioEventCollector(AudioEventListener nextListener) {
        this.nextListener = nextListener;
    }

    @Override
    public void audioBufferPlayed(int audioBufferId, double soundLevel) {
        synchronized (this) {
            if (firstBufferPlayed == null) {
                firstBufferPlayed = new AudioBufferInfoImpl(audioBufferId, soundLevel);
            }
            lastBufferPlayed = new AudioBufferInfoImpl(audioBufferId, soundLevel);
        }
        nextListener.audioBufferPlayed(audioBufferId, soundLevel);
    }

    @Override
    public void audioBufferRecorded(int audioBufferId, double soundLevel) {
        synchronized (this) {
            Assertions.illegalStateIfFalse(firstBufferPlayed == null,
            "Recorded buffer but recording not started.");
            minSoundLevel = Math.min(soundLevel, minSoundLevel);
            maxSoundLevel = Math.max(soundLevel, maxSoundLevel);
            recentlyRecordedBuffers.add(new AudioBufferInfoImpl(audioBufferId, soundLevel));
        }
        nextListener.audioBufferRecorded(audioBufferId, soundLevel);
    }

    @Override
    public void playingStarted() {
        synchronized (this) {
            firstBufferPlayed = null;
            lastBufferPlayed = null;
        }
        nextListener.playingStarted();
    }

    @Override
    public void playingStopped() {
        nextListener.playingStopped();
    }

    @Override
    public synchronized void recordingStarted() {
        synchronized (this) {
            firstBufferPlayed = null;
            lastBufferPlayed = null;
        }
        nextListener.recordingStarted();
    }

    @Override
    public void recordingStopped() {
        nextListener.recordingStopped();
    }

    @Override
    public synchronized double getMinSoundLevel() {
        return minSoundLevel;
    }

    @Override
    public synchronized double getMaxSoundLevel() {
        return maxSoundLevel;
    }

    @Override
    public synchronized Iterator<? extends AudioBufferInfo> getIteratorOverAudioEventsToPlot(
            int plotWidth) {
        moveRecentlyRecordedBuffersToRecordedBuffers();
        // This invalidates iterator returned by the previous call to
        // getIteratorOverAudioEventsToPlot, which is OK according to the
        // contract.
        removeOldRecordedBuffers();

        if (isPlaying() && !isOnPlotOfRecentlyRecordedBuffers(firstBufferPlayed, plotWidth)) {
            return centerPlotOn(lastBufferPlayed, plotWidth);
        } else {
            return recordedBuffers.descendingIterator();
        }
    }

    private boolean isPlaying() {
        return firstBufferPlayed != null;
    }

    private boolean isOnPlotOfRecentlyRecordedBuffers(AudioBufferInfoImpl audioBuffer, int plotWidth) {
        return (recordedBuffers.tailSet(audioBuffer).size() <= plotWidth);
    }

    private Iterator<AudioBufferInfoImpl> centerPlotOn(AudioBufferInfoImpl audioBufferToCenter,
            int plotWidth) {
        // Point the iterator at the buffer to be placed in the center.
        final Iterator<AudioBufferInfoImpl> iterator =
            recordedBuffers.tailSet(audioBufferToCenter).iterator();
        AudioBufferInfoImpl startBuffer = recordedBuffers.last();
        // Move the iterator by half of the plot width, so the centered buffer
        // is actually in the center.
        for (int i = 0; i <= plotWidth / 2; ++i) {
            if (!iterator.hasNext()) {
                break;
            }
            startBuffer = iterator.next();
        }
        // This is equivalent to reversing the direction of the iterator that
        // was adjusted in previous steps.
        return recordedBuffers.descendingSet().tailSet(startBuffer).iterator();
    }

    private void removeOldRecordedBuffers() {
        while (recordedBuffers.size() > HISTORY_SIZE) {
            recordedBuffers.remove(recordedBuffers.first());
        }
    }

    private void moveRecentlyRecordedBuffersToRecordedBuffers() {
        while (!recentlyRecordedBuffers.isEmpty()) {
            recordedBuffers.add(recentlyRecordedBuffers.remove());
        }
    }
}