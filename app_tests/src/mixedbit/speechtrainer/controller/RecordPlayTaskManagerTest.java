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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import mixedbit.speechtrainer.controller.RecordPlayTaskManager.RecordPlayTaskPriority;
import mixedbit.speechtrainer.controller.RecordPlayTaskManager.RecordPlayTaskState;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IMocksControl;

public class RecordPlayTaskManagerTest extends TestCase {
    private RecordPlayStrategy mockStrategy;
    private Recorder mockRecorder;
    private Player mockPlayer;
    private ExecutorService executorService;
    private RecordPlayTaskManager recordPlayTaskManager;
    private IMocksControl control;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        control = EasyMock.createStrictControl();
        mockStrategy = control.createMock(RecordPlayStrategy.class);
        mockRecorder = control.createMock(Recorder.class);
        mockPlayer = control.createMock(Player.class);
        executorService = Executors.newSingleThreadExecutor();
        recordPlayTaskManager = new RecordPlayTaskManager(mockRecorder, mockPlayer,
                executorService, RecordPlayTaskPriority.TEST);
    }

    private void replayAll() {
        control.replay();
    }

    private void verifyAll() {
        control.verify();
    }

    /**
     * Waits for a task to finish. Unlike terminateTaskIfRunning,
     * waitForTaskToFinish does not request the task to terminate, but waits
     * until it finishes because of a request from the strategy.
     */
    private void waitForTaskToFinish() {
        executorService.shutdown();
        try {
            assertTrue(executorService.awaitTermination(60, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            fail("Task execution interrupted.");
        }
    }

    public void testStartAndStopRecording() {
        // The task starts in the recording state, recorder should be started.
        mockRecorder.startRecording();
        // Request the task to terminate after the first invocation of
        // handleRecord.
        EasyMock.expect(mockStrategy.handleRecord(mockRecorder)).andReturn(
                RecordPlayTaskState.TERMINATE);
        mockRecorder.stopRecording();
        replayAll();

        // Start recording.
        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        waitForTaskToFinish();
        verifyAll();
        recordPlayTaskManager.terminateTaskIfRunning();
    }

    public void testStartAndStopPlaying() {
        // The task starts in the playing state, player should be started.
        mockPlayer.startPlaying();
        // Request the task to terminate after the first invocation of
        // handleRecord.
        EasyMock.expect(mockStrategy.handlePlay(mockPlayer)).andReturn(
                RecordPlayTaskState.TERMINATE);
        mockPlayer.stopPlaying();
        replayAll();

        // Start playing.
        recordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, mockStrategy);
        waitForTaskToFinish();
        verifyAll();
        recordPlayTaskManager.terminateTaskIfRunning();
    }

    public void testRecorderCalledUntilStrategyRequestsTermination() {
        mockRecorder.startRecording();
        // First invocations of handleRecord request the task to continue
        // recording (call handleRecord again). The last invocation requests the
        // task to terminate. handleRecord should not be called again.
        EasyMock.expect(mockStrategy.handleRecord(mockRecorder)).andReturn(
                RecordPlayTaskState.RECORD).times(10).andReturn(RecordPlayTaskState.TERMINATE);

        mockRecorder.stopRecording();
        replayAll();

        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        waitForTaskToFinish();
        verifyAll();
        recordPlayTaskManager.terminateTaskIfRunning();
    }

    public void testPlayerCalledUntilStrategyRequestsTermination() {
        mockPlayer.startPlaying();
        // First invocations of handlePlay request the task to continue
        // playing (call handlePlay again). The last invocation requests the
        // task to terminate. handlePlay should not be called again.
        EasyMock.expect(mockStrategy.handlePlay(mockPlayer)).andReturn(RecordPlayTaskState.PLAY)
        .times(10).andReturn(RecordPlayTaskState.TERMINATE);
        mockPlayer.stopPlaying();
        replayAll();

        recordPlayTaskManager.startTask(RecordPlayTaskState.PLAY, mockStrategy);
        waitForTaskToFinish();
        verifyAll();
        recordPlayTaskManager.terminateTaskIfRunning();
    }

    public void testSwichBetweenRecordingAndPlaying() {
        // The task starts in the recording state, recorder should be started.
        mockRecorder.startRecording();
        // The first invocation of handleRecord requests the task to switch to
        // the playing state.
        EasyMock.expect(mockStrategy.handleRecord(mockRecorder))
        .andReturn(RecordPlayTaskState.PLAY);
        // Recorder should be stopped.
        mockRecorder.stopRecording();
        // And Player should be started.
        mockPlayer.startPlaying();
        // The first invocation of handlePlay requests the task to switch to the
        // recording state again.
        EasyMock.expect(mockStrategy.handlePlay(mockPlayer)).andReturn(RecordPlayTaskState.RECORD);
        // Player should be stopped.
        mockPlayer.stopPlaying();
        // And Recorder started.
        mockRecorder.startRecording();
        // Finally, the task is requested to terminate.
        EasyMock.expect(mockStrategy.handleRecord(mockRecorder)).andReturn(
                RecordPlayTaskState.TERMINATE);
        mockRecorder.stopRecording();
        replayAll();

        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        waitForTaskToFinish();
        verifyAll();
        recordPlayTaskManager.terminateTaskIfRunning();
    }

    public void testAsynchronousTermination() throws InterruptedException {
        // Latch to ensure terminateTaskIfRunning is called after Recorder was
        // started and handleRecord was called at least handleRecordMinCallCount
        // times.
        final int handleRecordMinCallCount = 1000;
        final CountDownLatch handleRecordCalledLatch = new CountDownLatch(handleRecordMinCallCount);

        mockRecorder.startRecording();
        // handleRecord signals that it was called and repetitively returns
        // RECORD (requests handleRecord to be called again). If asynchronous
        // termination did not work, the test would run forever.
        EasyMock.expect(mockStrategy.handleRecord(mockRecorder)).andAnswer(
                new IAnswer<RecordPlayTaskState>() {
                    @Override
                    public RecordPlayTaskState answer() throws Throwable {
                        handleRecordCalledLatch.countDown();
                        return RecordPlayTaskState.RECORD;
                    }

                }).times(handleRecordMinCallCount).andReturn(RecordPlayTaskState.RECORD).anyTimes();
        mockRecorder.stopRecording();
        replayAll();

        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);

        // Wait for the task to execute handleRecord at least
        // handleRecordMinCallCount times.
        handleRecordCalledLatch.await();

        // Request the task to terminate and wait until it exits.
        recordPlayTaskManager.terminateTaskIfRunning();
        verifyAll();
    }

    public void testTwoTasksNotStartedSimultaneously() {
        // The task will die because mocks are not configured, but
        // terminateTask needs to be called anyway before the next task can be
        // started.
        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        try {
            recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
            fail("Manager allowed the second task to be started while the first task was not "
                    + "terminated");
        } catch (final AssertionError e) {
            // Expected.
        }
    }

    public void testTwoTasksCanBeStartedSequentially() {
        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        recordPlayTaskManager.terminateTaskIfRunning();
        recordPlayTaskManager.startTask(RecordPlayTaskState.RECORD, mockStrategy);
        recordPlayTaskManager.terminateTaskIfRunning();
    }
}
