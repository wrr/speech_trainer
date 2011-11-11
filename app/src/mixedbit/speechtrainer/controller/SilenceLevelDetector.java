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

import mixedbit.speechtrainer.SpeechTrainerConfig;
import android.util.Log;

/**
 * Calculates silence level based on the most recent measurements of sound
 * level. The combined length of measures taken into account is at most
 * SOUND_LEVEL_HISTORY_LENGTH_S seconds, older measures are discarded. This
 * allows for the silence level to increase when background noise increases.
 * Silence level is SILENCE_LEVEL_MARGIN above the smallest measure.
 */
class SilenceLevelDetector {
    // Sound level measure is discarded if combined length of newer measures
    // exceeds this const.
    public static final int SOUND_LEVEL_HISTORY_LENGTH_S = 10;
    // Silence level is that much above the smallest measure.
    public static final int SILENCE_LEVEL_MARGIN = 5;
    // Keeps the most recent measures of the sound level.
    private final BoundedPriorityQueue<Double> recentSoundLevels = new BoundedPriorityQueue<Double>(
            SOUND_LEVEL_HISTORY_LENGTH_S * SpeechTrainerConfig.numberOfBuffersPerSecond());

    public void addSoundLevelMeasurement(double soundLevel) {
        recentSoundLevels.add(soundLevel);
        // TODO: remove all logs
        Log.i(SpeechTrainerConfig.LOG_TAG, "Silence level " + getSilenceLevel() + " sound level "
                + soundLevel);
    }

    public boolean isAboveSilenceLevel(double bufferSoundLevel) {
        return bufferSoundLevel > getSilenceLevel();
    }

    private double getSilenceLevel() {
        return recentSoundLevels.getMin() + SILENCE_LEVEL_MARGIN;
    }
}
