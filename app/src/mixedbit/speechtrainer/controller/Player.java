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

import mixedbit.speechtrainer.controller.AudioBufferAllocator.AudioBuffer;
import android.media.AudioTrack;

/**
 * Wrapper over AudioTrack that exposes minimal interface for playing
 * AudioBuffers. Informs AudioEventListener about each executed action (playing
 * started, audio buffer played, playing stopped). The interface is extracted
 * from the PlayerImpl class to allow mocking with the standard EasyMock.
 * EasyMock fails to mock classes that depend on Android classes (AudioTrack in
 * this case).
 */
interface Player {

    /**
     * Starts playing. Calls to playAudioBuffer are allowed only after playing
     * was started.
     */
    public abstract void startPlaying();

    /**
     * Blocking call that plays an audio buffer. Requires playing to be started
     * (startPlaying called).
     */
    public abstract void playAudioBuffer(AudioBuffer audioBuffer);

    /**
     * Stops playing. Playing can be started again with the startPlaying method.
     */
    public abstract void stopPlaying();

}

class PlayerImpl implements Player {
    private final AudioTrack audioTrack;
    private final AudioEventListener audioEventListener;

    /**
     * @param audioTrack
     *            AudioTrack object configured by a caller.
     * @param audioEventListener
     *            Listener that is informed about each action executed by the
     *            player.
     */
    public PlayerImpl(AudioTrack audioTrack, AudioEventListener audioEventListener) {
        this.audioTrack = audioTrack;
        this.audioEventListener = audioEventListener;
    }

    @Override
    public void startPlaying() {
        audioTrack.play();
        this.audioEventListener.playingStarted();
    }

    @Override
    public void playAudioBuffer(AudioBuffer audioBuffer) {
        audioTrack.write(audioBuffer.getAudioData(), 0, audioBuffer.getAudioDataLengthInShorts());
        audioEventListener.audioBufferPlayed(
                audioBuffer.getAudioBufferId(), audioBuffer.getSoundLevel());
    }

    @Override
    public void stopPlaying() {
        audioTrack.stop();
        this.audioEventListener.playingStopped();
    }

}
