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

import mixedbit.speechtrainer.controller.RecordPlayTaskManager.RecordPlayTaskState;

public class InteractiveTrainingControllerTest extends TrainingControllerTest {
    private InteractiveTrainingController trainingController;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        trainingController = new InteractiveTrainingController(mockRecordPlayTaskManager,
                audioBufferAllocator);
    }

    @Override
    protected TrainingController getTrainingController() {
        return trainingController;
    }

    public void testRecordTerminatesCurrentTaskAndStartsRecordingTask() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
        replayAll();

        trainingController.record();
        verifyAll();
    }

    public void testPlayTerminatesCurrentTaskAndStartsPlayingTask() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, trainingController);
        replayAll();

        trainingController.startTraining();
        trainingController.play();
        verifyAll();
    }

    public void testRecordingAndPlaying() {
        final int numberOfBuffersToRecord = 7;
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);

        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, trainingController);
        replayAll();

        trainingController.startTraining();
        // Start recording and record numberOfBuffersToRecord audio buffers.
        trainingController.record();
        for (short i = 0; i < numberOfBuffersToRecord; i++) {
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
            assertEquals(i + 1, testRecorder.getRecordedBuffersCount());
        }

        // Start playing. Make sure handlePlay requests playing to continue
        // until all recorded buffers are played.
        trainingController.play();
        for (short i = 0; i < numberOfBuffersToRecord; i++) {
            assertEquals(RecordPlayTaskState.PLAY, trainingController.handlePlay(testPlayer));
            // Make sure one buffer was played.
            assertEquals(i + 1, testPlayer.getPlayedBuffersCount());
            // Make sure played buffer was a correct one (audio data contains
            // correct id).
            assertEquals(1, testPlayer.getLastPlayedAudioDataCopy().length);
            assertEquals(i, testPlayer.getLastPlayedAudioDataCopy()[0]);
        }

        verifyAll();
    }

    public void testReplay() {
        // The test is similar to the previous one, but play is invoked twice.
        // Second invocation should still play the recorded data.
        final int numberOfBuffersToRecord = 7;
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);

        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, trainingController);

        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, trainingController);

        replayAll();

        trainingController.startTraining();
        trainingController.record();
        for (short i = 0; i < numberOfBuffersToRecord; i++) {
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
            assertEquals(i + 1, testRecorder.getRecordedBuffersCount());
        }

        trainingController.play();
        trainingController.play();

        for (short i = 0; i < numberOfBuffersToRecord; i++) {
            assertEquals(RecordPlayTaskState.PLAY, trainingController.handlePlay(testPlayer));
            assertEquals(i + 1, testPlayer.getPlayedBuffersCount());
            assertEquals(1, testPlayer.getLastPlayedAudioDataCopy().length);
            assertEquals(i, testPlayer.getLastPlayedAudioDataCopy()[0]);
        }

        verifyAll();
    }

    public void testPlayingTerminatesWhenNoRecordedBuffers() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, trainingController);
        replayAll();

        trainingController.startTraining();
        trainingController.play();
        // No audio buffers recorded, handlePlay should request RecordPlayTask
        // to terminate (return TERMINATE status).
        assertEquals(RecordPlayTaskState.TERMINATE, trainingController.handlePlay(testPlayer));
        assertEquals(0, testPlayer.getPlayedBuffersCount());
        verifyAll();
    }

    public void testRecordingTerminatesWhenNoMoreAudioBuffers() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
        replayAll();

        trainingController.startTraining();
        trainingController.record();
        for (int i = 0; i < NUMBER_OF_AUDIO_BUFFERS; ++i) {
            // As long as there are audio buffers available, handleRecord should
            // request RecordPlayTask to continue recording (return RECORD
            // status).
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
            // handleRecord should invoke Recorder to record each allocated
            // audio buffer.
            assertEquals(i + 1, testRecorder.getRecordedBuffersCount());
        }
        // No more audio buffers, handleRecord should request RecordPlayTask to
        // terminate (return TERMINATE status).
        assertEquals(RecordPlayTaskState.TERMINATE, trainingController.handleRecord(testRecorder));
        assertEquals(NUMBER_OF_AUDIO_BUFFERS, testRecorder.getRecordedBuffersCount());
        verifyAll();
    }

    public void testRecordingTerminatesWhenRecordAudioBufferFails() {
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
        replayAll();

        trainingController.startTraining();
        trainingController.record();
        assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
        // The next recordAudioBuffer call should fail and handleRecord should
        // request RecordPlayTask to terminate.
        testRecorder.setRecordAudioBufferResult(false);
        assertEquals(RecordPlayTaskState.TERMINATE, trainingController.handleRecord(testRecorder));

        verifyAll();
    }
    public void testRecordingDiscardsAllPreviouslyRecordedAudioData() {
        // Start recording task twice and make sure that data recorded by the
        // first task is discarded when the second task is started.
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
        mockRecordPlayTaskManager.terminateTaskIfRunning();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
        replayAll();

        trainingController.startTraining();
        trainingController.record();
        for (int i = 0; i < NUMBER_OF_AUDIO_BUFFERS; ++i) {
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
        }
        assertEquals(NUMBER_OF_AUDIO_BUFFERS, testRecorder.getRecordedBuffersCount());
        // All audio buffers should be allocated.
        assertNull(audioBufferAllocator.allocateAudioBuffer());

        // Start recording again.
        trainingController.record();
        // All audio buffers should be released.
        audioBufferAllocator.assertAllAudioBuffersAvailable();
        verifyAll();
    }
}
