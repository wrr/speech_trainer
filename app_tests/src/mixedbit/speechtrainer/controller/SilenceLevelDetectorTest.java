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

import mixedbit.speechtrainer.controller.SilenceLevelDetector;
import junit.framework.TestCase;

/**
 * SilenceLevelDetector is a very simple wrapper over BoundedPriorityQueue, the
 * functionality is mostly covered by BoundedPriorityQueueTest.
 */
public class SilenceLevelDetectorTest extends TestCase {

    private final SilenceLevelDetector silenceLevelDetector = new SilenceLevelDetector();

    public void testSilenceLevelIsSilenceLevelMarginAboveTheSmallestMeasure() {
        silenceLevelDetector.addSoundLevelMeasurement(123);
        silenceLevelDetector.addSoundLevelMeasurement(155);
        silenceLevelDetector.addSoundLevelMeasurement(111);

        assertFalse(silenceLevelDetector.isAboveSilenceLevel(111));
        assertFalse(silenceLevelDetector.isAboveSilenceLevel(
                111 + SilenceLevelDetector.SILENCE_LEVEL_MARGIN));
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(
                111 + SilenceLevelDetector.SILENCE_LEVEL_MARGIN + 1.0));
    }

    public void testOldMeasuresAreDiscarded() {
        silenceLevelDetector.addSoundLevelMeasurement(111);
        assertTrue(silenceLevelDetector.isAboveSilenceLevel(200));

        // Keep adding measures until the first measure is discarded. This
        // wouldn't terminate if old measures were not removed.
        while (silenceLevelDetector.isAboveSilenceLevel(200)) {
            silenceLevelDetector.addSoundLevelMeasurement(200);
        }
    }

}
