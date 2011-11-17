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
import mixedbit.speechtrainer.SpeechTrainerConfig;
import mixedbit.speechtrainer.controller.SilenceFilter;
import mixedbit.speechtrainer.controller.SilenceLevelDetector;
import mixedbit.speechtrainer.controller.SilenceFilter.Action;
import mixedbit.speechtrainer.controller.SilenceFilter.FilterResult;

import org.easymock.EasyMock;


public class SilenceFilterTest extends TestCase {
    SilenceLevelDetector mockSilenceLevelDetector;
    SilenceFilter silenceFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockSilenceLevelDetector = EasyMock.createStrictMock(SilenceLevelDetector.class);
        silenceFilter = new SilenceFilter(mockSilenceLevelDetector);
    }

    private void replayAll() {
        EasyMock.replay(mockSilenceLevelDetector);
    }

    private void verifyAll() {
        EasyMock.verify(mockSilenceLevelDetector);
    }

    /**
     * Helper to configure mockSilenceLevelDetector to expect a measure with a
     * given sound level to be added and to answer true when asked if this
     * measure is above silence level.
     */
    private void expectSoundAboveSilenceLevel(double soundLevel) {
        mockSilenceLevelDetector.addSoundLevelMeasurement(soundLevel);
        EasyMock.expect(mockSilenceLevelDetector.isAboveSilenceLevel(soundLevel)).andReturn(true);
    }

    /**
     * Helper to configure mockSilenceLevelDetector to expect a measure with a
     * given sound level to be added and to answer false when asked if this
     * measure is above silence level.
     */
    private void expectSoundBelowSilenceLevel(double soundLevel) {
        mockSilenceLevelDetector.addSoundLevelMeasurement(soundLevel);
        EasyMock.expect(mockSilenceLevelDetector.isAboveSilenceLevel(soundLevel)).andReturn(false);
    }

    private int BufferLengthMsToShorts(int bufferLengthMs) {
        int result = bufferLengthMs * SpeechTrainerConfig.SAMPLE_RATE_HZ / 1000;
        // If buffer length was rounded down, increase it to make sure it can
        // hold audio data of length bufferLengthMs.
        if ((bufferLengthMs * SpeechTrainerConfig.SAMPLE_RATE_HZ) % 1000 != 0) {
            result += 1;
        }
        return result;
    }

    public void testBufferAboveSilenceLevelIsAccepted() {
        final double soundLevelAboveSilence = 72;
        final int bufferLength = 1000;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        replayAll();

        final FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                bufferLength);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());
        verifyAll();
    }

    public void testBufferBelowSilenceLevelIsDroppedIfNoBuffersAreAccepted() {
        // If no buffers are accepted, a silence buffer is always rejected,
        // because it can't be silence between meaningful audio data.
        final double soundLevelBelowSilence = 72;
        final int bufferLength = 1000;

        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        final FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                bufferLength);
        assertEquals(Action.DROP_ALL_ACCEPTED_BUFFERS, result.getAction());
        verifyAll();
    }

    public void testBufferBelowSilenceLevelIsAcceptedIfOtherBufferIsAlreadyAccepted() {
        // If some buffers are accepted, a short silence buffer is not rejected.
        // It can be a silence between meaningful audio data.
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;
        final int bufferLength = 1000;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        // Filter a buffer above silence level.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                bufferLength);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Filter a buffer below silence level. The buffer is not long enough to
        // request already collected data to be played.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS) - 1);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());
        verifyAll();
    }

    public void testLongSilenceRequestsBuffersToBePlayedIfMeaningfulDataLongEnough() {
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        // Filter a buffer above silence level that is long enough to deserve
        // playing.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS));
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Filter a buffer below silence level that is long enough to request
        // already collected data to be played.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS));
        assertEquals(Action.DROP_TRAILING_BUFFERS_AND_PLAY, result.getAction());
        // Trailing buffers with silence should be dropped. In real life,
        // buffers are shorter and there are several trailing buffers with
        // silence out of which only few are dropped
        // (testPartOfTrailingSilenceIsDroppedWhenPlayingIsRequested tests
        // this).
        assertEquals(1, result.getNumberOfTrailingBuffersToDrop());
        verifyAll();
    }

    public void testLongSilenceDiscardsAllInputIfMeaningfulDataNotLongEnough() {
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        // Filter a buffer above silence level that is not long enough to
        // deserve playing.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS) - 1);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Filter a buffer below silence level that is long enough to request
        // already collected data to be dropped (because it is too short to be
        // played).
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS));
        assertEquals(Action.DROP_ALL_ACCEPTED_BUFFERS, result.getAction());
        verifyAll();
    }

    public void testPartOfTrailingSilenceIsDroppedWhenPlayingIsRequested() {
        // When a period of silence of length LONG_SILENCE_INTERVAL_MS is
        // detected, collected buffers should be requested to be played.
        // Trailing buffers of silence with combined length larger or
        // equal to TRAILING_SILENCE_TO_DROP_MS are requested to be
        // dropped. This is to reduce delay between player stopping playing
        // meaningful audio data and recorder starting recording again. Dropping
        // all trailing silence buffers does not work well, because it makes
        // playing terminate too abruptly.
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        final int singleSilenceBufferLengthMs = SilenceFilter.TRAILING_SILENCE_TO_DROP_MS / 10;

        for (int silenceBuffersCombinedLengthMs = 0;
            silenceBuffersCombinedLengthMs < SilenceFilter.LONG_SILENCE_INTERVAL_MS;
            silenceBuffersCombinedLengthMs += singleSilenceBufferLengthMs) {
            expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        }
        replayAll();

        // Filter a buffer above silence level that is long enough to deserve
        // playing.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS));
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Filter several short buffers below silence level with combined length
        // smaller than LONG_SILENCE_INTERVAL_MS.
        for (int silenceBuffersCombinedLengthMs = 0;
            silenceBuffersCombinedLengthMs + singleSilenceBufferLengthMs
                < SilenceFilter.LONG_SILENCE_INTERVAL_MS;
            silenceBuffersCombinedLengthMs += singleSilenceBufferLengthMs) {
            result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                    BufferLengthMsToShorts(singleSilenceBufferLengthMs));
            assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        }
        // Filter one more buffer below silence level. Combined length of
        // silence should now exceed LONG_SILENCE_INTERVAL_MS and filter should
        // request collected data to be played.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(singleSilenceBufferLengthMs));
        assertEquals(Action.DROP_TRAILING_BUFFERS_AND_PLAY, result.getAction());

        // Each buffer below silence level had length of 0.1 *
        // SilenceFilter.TRAILING_SILENCE_TO_DROP_MS, so 10 last buffers
        // should be dropped.
        assertEquals(10, result.getNumberOfTrailingBuffersToDrop());
        verifyAll();
    }

    public void testBuffersCollectedUntilLongSilenceDetected() {
        // Checks that several buffers above silence level interleaved with
        // short silence buffers are accepted until long silence is detected.
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        // Buffer above silence level, should be accepted regardless of
        // its length.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS) - 100);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Short buffer below silence level should be accepted.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS) - 1);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Another buffer above silence level.
        result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                2 * BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS));
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Two buffers below silence level. Should be accepted because combined
        // length of the two is lower than LONG_SILENCE_INTERVAL_MS.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS) / 2 - 1);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS) / 2 - 1);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Another buffer above silence level.
        result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                2 * BufferLengthMsToShorts(SilenceFilter.MIN_LENGTH_OF_MEANINGFUL_DATA_TO_PLAY_MS));
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // And finally a long buffer below silence level. Should request all
        // accepted buffers to be played.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS));
        assertEquals(Action.DROP_TRAILING_BUFFERS_AND_PLAY, result.getAction());

        verifyAll();
    }

    public void testResetFilterState() {
        final double soundLevelAboveSilence = 72;
        final double soundLevelBelowSilence = 32;
        final int bufferLength = 1000;

        expectSoundAboveSilenceLevel(soundLevelAboveSilence);
        expectSoundBelowSilenceLevel(soundLevelBelowSilence);
        replayAll();

        // Filter a buffer above silence level.
        FilterResult result = silenceFilter.filterRecorderBuffer(soundLevelAboveSilence,
                bufferLength);
        assertEquals(Action.ACCEPT_BUFFER, result.getAction());

        // Reset the SilenceFilter state. It should forget about the accepted
        // buffer.
        silenceFilter.reset();

        // Filter a short buffer below silence level. The buffer should be
        // dropped, because there are no other accepted buffers (the
        // filter was reset), so it can not be silence between meaningful
        // audio data.
        result = silenceFilter.filterRecorderBuffer(soundLevelBelowSilence,
                BufferLengthMsToShorts(SilenceFilter.LONG_SILENCE_INTERVAL_MS) - 1);
        assertEquals(Action.DROP_ALL_ACCEPTED_BUFFERS, result.getAction());
        verifyAll();
    }

    public void testNumberOfTrailingBuffersToDropIncorrectAccessDetected() {
        // GetNumberOfTrailingBuffersToDrop can be called only when Action in
        // FilterResult is DROP_TRAILING_BUFFERS_AND_PLAY.
        FilterResult filterResult = silenceFilter.new FilterResult(
                Action.DROP_TRAILING_BUFFERS_AND_PLAY, 12);
        assertEquals(Action.DROP_TRAILING_BUFFERS_AND_PLAY, filterResult.getAction());
        assertEquals(12, filterResult.getNumberOfTrailingBuffersToDrop());

        filterResult = silenceFilter.new FilterResult(Action.ACCEPT_BUFFER);
        assertEquals(Action.ACCEPT_BUFFER, filterResult.getAction());
        try {
            filterResult.getNumberOfTrailingBuffersToDrop();
            fail("Illegal state not detected.");
        } catch (final IllegalStateException e) {
            // Expected.
        }
    }
}
