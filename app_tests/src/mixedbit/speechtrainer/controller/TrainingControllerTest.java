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

import org.easymock.EasyMock;
import org.easymock.IMocksControl;


/**
 * Common base class for TrainingControllers tests.
 */
public abstract class TrainingControllerTest extends TestCase {
    /**
     * Fake recorder that sets unique, monotonically increasing number as an
     * audio data when readAudioBuffer is called. Makes sure startRecording and
     * stopRecording are never called (RecordPlayTaskManager is responsible for
     * calling these). Keeps track of the total number of recorded buffers.
     */
    protected class TestRecorder implements Recorder {
        private short recordedBuffersCount = 0;
        private double lastRecordedAudioBufferSoundLevel = -1.0;
        private boolean readAudioBufferResult = true;

        @Override
        public void startRecording() {
            fail("startRecording should not be called by TrainingController.");
        }

        @Override
        public boolean readAudioBuffer(AudioBuffer audioBuffer) {
            assertTrue(audioBuffer.getAudioData().length == AUDIO_BUFFER_SIZE);
            audioBuffer.getAudioData()[0] = recordedBuffersCount;
            audioBuffer.audioDataStored(1);
            lastRecordedAudioBufferSoundLevel = audioBuffer.getSoundLevel();
            assertTrue(Short.MAX_VALUE != recordedBuffersCount);
            recordedBuffersCount += 1;
            return readAudioBufferResult;
        }

        @Override
        public void stopRecording() {
            fail("stopRecording should not be called by TrainingController.");
        }

        public void setReadAudioBufferResult(boolean readAudioBufferResult) {
            this.readAudioBufferResult = readAudioBufferResult;
        }

        public short getRecordedBuffersCount() {
            return recordedBuffersCount;
        }

        public int getLastRecordedAudioBufferLengthInShorts() {
            return 1;
        }

        public double getLastRecordedAudioBufferSoundLevel() {
            assertTrue(lastRecordedAudioBufferSoundLevel >= 0);
            return lastRecordedAudioBufferSoundLevel;
        }
    };

    /**
     * Fake player that keeps copy of the last played audio data. Makes sure
     * startPlaying and stopPlayin are never called (RecordPlayTaskManager is
     * responsible for calling these). Keeps track of the total number of played
     * buffers.
     */
    protected class TestPlayer implements Player {
        private short playedBuffersCount = 0;
        private short[] lastPlayedAudioDataCopy;

        @Override
        public void startPlaying() {
            fail("startPlaying should not be called by TrainingController.");
        }

        @Override
        public void writeAudioBuffer(AudioBuffer audioBuffer) {
            assertTrue(audioBuffer.getAudioData().length == AUDIO_BUFFER_SIZE);
            copyRecordedAudioData(audioBuffer);
            assertTrue(Short.MAX_VALUE != playedBuffersCount);
            playedBuffersCount += 1;
        }

        @Override
        public void stopPlaying() {
            fail("stopPlaying should not be called by TrainingController.");
        }

        public short getPlayedBuffersCount() {
            return playedBuffersCount;
        }

        public short[] getLastPlayedAudioDataCopy() {
            return lastPlayedAudioDataCopy;
        }

        private void copyRecordedAudioData(AudioBuffer audioBuffer) {
            lastPlayedAudioDataCopy = new short[audioBuffer.getAudioDataLengthInShorts()];
            for (int i = 0; i < audioBuffer.getAudioDataLengthInShorts(); i++) {
                lastPlayedAudioDataCopy[i] = audioBuffer.getAudioData()[i];
            }
        }

    }

    protected static final int NUMBER_OF_AUDIO_BUFFERS = 10;
    protected static final int AUDIO_BUFFER_SIZE = 100;

    protected RecordPlayTaskManager mockRecordPlayTaskManager;
    protected AudioBufferAllocator audioBufferAllocator;
    protected TestRecorder testRecorder;
    protected TestPlayer testPlayer;
    protected IMocksControl control;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        control = EasyMock.createStrictControl();
        mockRecordPlayTaskManager = control.createMock(RecordPlayTaskManager.class);
        testRecorder = new TestRecorder();
        testPlayer = new TestPlayer();
        audioBufferAllocator = new AudioBufferAllocator(NUMBER_OF_AUDIO_BUFFERS, AUDIO_BUFFER_SIZE);
    }

    protected void replayAll() {
        control.replay();
    }

    protected void verifyAll() {
        control.verify();
    }

    protected abstract TrainingController getTrainingController();

    // Common test cases for TrainingControllers.

    public void testStopTrainingTerminatesCurrentTask() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        replayAll();

        getTrainingController().stopTraining();
        verifyAll();
    }

    public void testStartTrainingDiesIfAudioBufferLeaked() {
        try {
            // Leak a buffer.
            audioBufferAllocator.allocateAudioBuffer();
            // When startTraining is called all audio buffers should be returned
            // to the AudioBUfferAllocator. startTraining should die if audio
            // buffer is leaked.
            getTrainingController().startTraining();
            fail("Leaked audio buffer not detected.");
        } catch (final AssertionError e) {
            // Expected.
        }
    }
}
