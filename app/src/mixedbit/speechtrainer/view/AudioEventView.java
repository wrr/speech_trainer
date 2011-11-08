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

package mixedbit.speechtrainer.view;

import java.util.Iterator;

import mixedbit.speechtrainer.controller.AudioEventListener;
import mixedbit.speechtrainer.model.AudioBufferInfo;
import mixedbit.speechtrainer.model.AudioEventHistory;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.ImageView;

/**
 * Updates the UI in response to audio events. The UI holds following elements:
 * 
 * -A plot with sound levels of recently recorded or played buffers. The data to
 * be plotted is obtained from the AudioEventHistory. The plot is is updated
 * each time new buffer is recorded or played.
 * 
 * -Record status view that is enabled when recording starts and disabled when
 * recording ends.
 * 
 * The creator of the AudioEventView must set AudioEventHistory and
 * RecordStatusView before recording or playing is started.
 */
public class AudioEventView extends ImageButton implements AudioEventListener {
    private static final int RECORDED_BUFFER_COLOR = 0xffd00000;
    private static final int PLAYED_BUFFER_COLOR = 0xff990000;
    private final Paint recordedBufferPaint;
    private final Paint playedBufferPaint;
    private ImageView recordStatusView;
    private AudioEventHistory audioEventHistory;

    public AudioEventView(Context context, AttributeSet attrs) {
        super(context, attrs);
        recordedBufferPaint = new Paint();
        recordedBufferPaint.setColor(RECORDED_BUFFER_COLOR);
        playedBufferPaint = new Paint();
        playedBufferPaint.setColor(PLAYED_BUFFER_COLOR);
    }

    /**
     * Must be called with not null argument before recording or playing is
     * started for the first time.
     * 
     * @param recordStatusView
     *            A view to be enabled when recording starts and disabled when
     *            recording ends.
     */
    public void setRecordStatusView(ImageView recordStatusView) {
        this.recordStatusView = recordStatusView;
        recordStatusView.setEnabled(false);
    }

    /**
     * Must be called with not null argument before recording or playing is
     * started for the first time.
     * 
     * @param audioEventHistory
     *            Provides a data to be plotted.
     */
    public void setAudioEventHistory(AudioEventHistory audioEventHistory) {
        this.audioEventHistory = audioEventHistory;
    }


    @Override
    public void audioBufferPlayed(int audioBufferId, double soundLevel) {
        // Redraw the plot.
        postInvalidate();
    }

    @Override
    public void audioBufferRecorded(int audioBufferId, double soundLevel) {
        // Redraw the plot.
        postInvalidate();
    }

    @Override
    public void recordingStarted() {
        recordStatusView.getHandler().post(new Runnable() {
            @Override
            public void run() {
                recordStatusView.setEnabled(true);
            }
        });
    }

    @Override
    public void recordingStopped() {
        recordStatusView.getHandler().post(new Runnable() {
            @Override
            public void run() {
                recordStatusView.setEnabled(false);
            }
        });
    }

    @Override
    public void playingStarted() {
    }

    @Override
    public void playingStopped() {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();

        final Iterator<? extends AudioBufferInfo> buffersIterator =
            audioEventHistory.getIteratorOverAudioEventsToPlot(viewWidth);

        for (int i = 0; i < viewWidth && buffersIterator.hasNext(); ++i) {
            final AudioBufferInfo audioBufferInfo = buffersIterator.next();
            final int height = getHeightForSoundLevel(audioBufferInfo.getSoundLevel(), viewHeight);
            final int lineStart = (viewHeight - height) / 2;
            if (audioBufferInfo.isPlayed()) {
                canvas.drawRect(i, lineStart, i + 1, lineStart + height, playedBufferPaint);
            } else {
                canvas.drawRect(i, lineStart, i + 1, lineStart + height, recordedBufferPaint);
            }
        }
    }

    /**
     * Calculates height of a line to represent a given soundLevel. Makes sure
     * the lowest and the smallest recorded sound levels fit in the plot.
     */
    private int getHeightForSoundLevel(double soundLevel, int canvasHeight) {
        final double minSoundLevel = audioEventHistory.getMinSoundLevel();
        final double maxSoundLevel = audioEventHistory.getMaxSoundLevel();
        return (int) ((soundLevel - minSoundLevel) * (canvasHeight - 1)
                / (maxSoundLevel - minSoundLevel));
    }
}