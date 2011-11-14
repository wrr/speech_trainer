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

public class SilenceLevelDetectorTest extends TestCase {
    private static final double DELTA = 0.001;
    private final SilenceLevelDetector silenceLevelDetector = new SilenceLevelDetector();


    public void testFirstSampleIsTreatedAsSilence() {
        // The first sample should be treated as silence. Because it is the only
        // sample, it is equal to the mean value. The silence level should be
        // set to the mean + SILENCE_LEAVE_MARGIN.
        silenceLevelDetector.addSoundLevelMeasurement(123.0);

        assertFalse(silenceLevelDetector.isAboveSilenceLevel(123.0));
        assertFalse(silenceLevelDetector.isAboveSilenceLevel(
                123.0 + SilenceLevelDetector.SILENCE_LEAVE_MARGIN));
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(
                123.0 + SilenceLevelDetector.SILENCE_LEAVE_MARGIN + DELTA));
    }

    public void testSilenceLevelIsSilenceLeaveMarginAboveTheMeanWhenRecordingSilence() {
        // All samples are below the silence level.
        silenceLevelDetector.addSoundLevelMeasurement(105.0);
        silenceLevelDetector.addSoundLevelMeasurement(3.0);
        silenceLevelDetector.addSoundLevelMeasurement(6.0);

        // (105 + 3 + 6) / 3 = 37
        assertFalse(silenceLevelDetector
                .isAboveSilenceLevel(38.0 + SilenceLevelDetector.SILENCE_LEAVE_MARGIN));
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(38.0
                + SilenceLevelDetector.SILENCE_LEAVE_MARGIN + DELTA));

    }

    public void testSilenceLevelIsSilenceEnterMarginAboveTheMeanWhenRecordingMeaningfulSound() {
        silenceLevelDetector.addSoundLevelMeasurement(3.0);
        silenceLevelDetector.addSoundLevelMeasurement(6.0);
        // Add a sample above the silence level. It should not be included in
        // the mean calculation and it should cause the silence level to be set
        // SILENCE_ENTER_MARGIN above the mean.
        silenceLevelDetector.addSoundLevelMeasurement(105.0);

        // (3 + 6) / 2 = 4.5
        assertFalse(silenceLevelDetector
                .isAboveSilenceLevel(4.5 + SilenceLevelDetector.SILENCE_ENTER_MARGIN));
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(4.5
                + SilenceLevelDetector.SILENCE_ENTER_MARGIN + DELTA));

    }

    public void testSwitchFromSilenceEnterMarginToSilenceLeaveMargin() {
        silenceLevelDetector.addSoundLevelMeasurement(6.0);
        // Add a sample above the silence level. It should not be included in
        // the mean calculation and it should cause the silence level to be set
        // SILENCE_ENTER_MARGIN above the mean.
        silenceLevelDetector.addSoundLevelMeasurement(105.0);
        // Add a sample below the silence level. It should be included in the
        // mean calculation and it should cause the silence level to be set
        // SILENCE_LEAVE_MARGIN below the mean.
        silenceLevelDetector.addSoundLevelMeasurement(3.0);

        // (3 + 6) / 2 = 4.5
        assertFalse(silenceLevelDetector
                .isAboveSilenceLevel(4.5 + SilenceLevelDetector.SILENCE_LEAVE_MARGIN));
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(4.5
                + SilenceLevelDetector.SILENCE_LEAVE_MARGIN + DELTA));

    }

    public void testOldMeasuresAreDiscarded() {
        for (int i = 0; i < SilenceLevelDetector.SILENCE_HISTORY_LENGTH; i++) {
            silenceLevelDetector.addSoundLevelMeasurement(100.0);
        }
        // Adding SILENCE_HISTORY_LENGTH measures should discard all initial
        // measures.
        for (int i = 0; i < SilenceLevelDetector.SILENCE_HISTORY_LENGTH; i++) {
            silenceLevelDetector.addSoundLevelMeasurement(3.0);
        }
        // Make sure initial measures are not taken into account.
        assertTrue(silenceLevelDetector
                .isAboveSilenceLevel(3.0 + SilenceLevelDetector.SILENCE_LEAVE_MARGIN + DELTA));
    }

}
