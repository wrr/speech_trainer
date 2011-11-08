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

import mixedbit.speechtrainer.controller.AutomaticTrainingController;
import mixedbit.speechtrainer.controller.SilenceFilter;
import mixedbit.speechtrainer.controller.TrainingController;
import mixedbit.speechtrainer.controller.RecordPlayTaskManager.RecordPlayTaskState;
import mixedbit.speechtrainer.controller.SilenceFilter.Action;

import org.easymock.Capture;
import org.easymock.EasyMock;


public class AutomaticTrainingControllerTest extends TrainingControllerTest {
    private AutomaticTrainingController trainingController;
    private SilenceFilter mockSilenceFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockSilenceFilter = control.createMock(SilenceFilter.class);
        trainingController = new AutomaticTrainingController(mockRecordPlayTaskManager,
                mockSilenceFilter, audioBufferAllocator);
    }

    @Override
    protected TrainingController getTrainingController() {
        return trainingController;
    }

    private void expectTrainingStarted() {
        mockSilenceFilter.reset();
        mockRecordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, trainingController);
    }

    public void testStartTrainingResetsSilenceFilterAndStartsRecording() {
        expectTrainingStarted();
        replayAll();

        trainingController.startTraining();
        verifyAll();
    }

    public void testBufferRecordedAndPassedToSilenceFilter() {
        expectTrainingStarted();
        final Capture<Double> soundLevelCapture = new Capture<Double>();
        final Capture<Integer> bufferLengthCapture = new Capture<Integer>();
        // Recorded buffer should be passed to the silence filter.
        EasyMock.expect(
                mockSilenceFilter.filterRecorderBuffer(EasyMock.capture(soundLevelCapture),
                        EasyMock.capture(bufferLengthCapture))).andReturn(
                                mockSilenceFilter.new FilterResult(Action.ACCEPT_BUFFER));
        replayAll();

        trainingController.startTraining();
        // Buffer was accepted by the silence filter, handleRecord should
        // request RecordPlayTask to continue recording (return RECORD status).
        assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
        // Make sure one buffer was recorded.
        assertEquals(1, testRecorder.getRecordedBuffersCount());
        // Make sure correct length and sound level were passed to the silence
        // filter.
        assertEquals(testRecorder.getLastRecordedAudioBufferLengthInShorts(),
                bufferLengthCapture.getValue().intValue());
        assertEquals(testRecorder.getLastRecordedAudioBufferSoundLevel(),
                soundLevelCapture.getValue().doubleValue());
        verifyAll();
    }

    public void testRecordingAndPlayingCycle() {
        final int leadingBuffersToDrop = 3;
        final int buffersToPlay = 5;
        final int trailingBuffersToDrop = 2;
        final int totalBuffersToRecord = leadingBuffersToDrop + buffersToPlay
        + trailingBuffersToDrop;
        expectTrainingStarted();
        // Expect totalBuffersToRecord to be filtered. When a buffer number
        // leadingBufferToDrop is recorded, all buffers should be dropped. Next,
        // buffersToPlay + trailingBuffersToDrop - 1 buffers should be accepted.
        // When a buffer number buffersToPlay + trailingBuffersToDrop is passed
        // to the silence filter, the filter should request
        // trailingBuffersToDrop to be dropped and the rest to be played.
        EasyMock.expect(
                mockSilenceFilter.filterRecorderBuffer(EasyMock.anyDouble(), EasyMock.anyInt()))
                .andReturn(mockSilenceFilter.new FilterResult(Action.ACCEPT_BUFFER))
                .times(leadingBuffersToDrop - 1)
                .andReturn(mockSilenceFilter.new FilterResult(Action.DROP_ALL_ACCEPTED_BUFFERS))
                .times(1)
                .andReturn(mockSilenceFilter.new FilterResult(Action.ACCEPT_BUFFER))
                .times(buffersToPlay + trailingBuffersToDrop - 1)
                .andReturn(mockSilenceFilter.new FilterResult(
                        Action.DROP_TRAILING_BUFFERS_AND_PLAY, trailingBuffersToDrop))
                        .times(1);
        replayAll();

        trainingController.startTraining();
        for (int i = 0; i < totalBuffersToRecord - 1; ++i) {
            // handleRecord should request recording to continue until the
            // silence filter returns DROP_TRAILING_BUFFERS_AND_PLAY.
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
            assertEquals(i + 1, testRecorder.getRecordedBuffersCount());
        }
        assertEquals(RecordPlayTaskState.PLAY, trainingController.handleRecord(testRecorder));
        assertEquals(totalBuffersToRecord, testRecorder.getRecordedBuffersCount());

        // Playing should be requested only for buffers that were not dropped.
        for (int i = 0; i < buffersToPlay; ++i) {
            assertEquals(RecordPlayTaskState.PLAY, trainingController.handlePlay(testPlayer));
            assertEquals(i + 1, testPlayer.getPlayedBuffersCount());
            assertEquals(1, testPlayer.getLastPlayedAudioDataCopy().length);
            // Verify that correct buffers are played and in a correct order.
            // Audio data from the TestRecorded consist of consecutive integers.
            // Buffers with audio data [0..leadingBufferToDrop) should be
            // rejected, [leadingBufferToDrop, leadingBufferToDrop +
            // buffersToPlay) should be played.
            assertEquals(i + leadingBuffersToDrop, testPlayer.getLastPlayedAudioDataCopy()[0]);
        }
        assertEquals(RecordPlayTaskState.RECORD, trainingController.handlePlay(testPlayer));
        assertEquals(buffersToPlay, testPlayer.getPlayedBuffersCount());
        // Each played buffer should be released (AutomaticTrainingController
        // does not support replay).
        audioBufferAllocator.assertAllAudioBuffersAvailable();
        verifyAll();
    }

    public void testRecordingTerminatesWhenNoMoreAudioBuffers() {
        expectTrainingStarted();
        // Configure the silence filter to accept each buffer and never request
        // playing to be started.
        EasyMock.expect(
                mockSilenceFilter.filterRecorderBuffer(EasyMock.anyDouble(), EasyMock.anyInt()))
                .andReturn(mockSilenceFilter.new FilterResult(Action.ACCEPT_BUFFER)).anyTimes();
        replayAll();

        trainingController.startTraining();
        for (int i = 0; i < NUMBER_OF_AUDIO_BUFFERS; ++i) {
            // As long as there are audio buffers available, handleRecord should
            // request RecordPlayTask to continue recording (return RECORD
            // status).
            assertEquals(RecordPlayTaskState.RECORD, trainingController.handleRecord(testRecorder));
            // Make sure Recorder was invoked.
            assertEquals(i + 1, testRecorder.getRecordedBuffersCount());
        }
        // No more audio buffers, handleRecord should request RecordPlayTask to
        // start playing (return PLAY status).
        assertEquals(RecordPlayTaskState.PLAY, trainingController.handleRecord(testRecorder));
        assertEquals(NUMBER_OF_AUDIO_BUFFERS, testRecorder.getRecordedBuffersCount());
        verifyAll();
    }
}
