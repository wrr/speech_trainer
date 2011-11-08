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

import mixedbit.speechtrainer.R;
import mixedbit.speechtrainer.TrainingApplication;
import mixedbit.speechtrainer.controller.AutomaticTrainingController;
import mixedbit.speechtrainer.controller.ControllerFactory;
import mixedbit.speechtrainer.controller.InteractiveTrainingController;
import mixedbit.speechtrainer.controller.TrainingController;
import mixedbit.speechtrainer.model.AudioEventCollector;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.ImageView;

/**
 * Interacts with the user during the training session.
 */
public class TrainingActivity extends Activity implements OnSharedPreferenceChangeListener {
    private ImageButton recordButton;
    private ImageButton replayButton;
    private View horizontalDividerView;
    private TrainingController activeTrainingController;
    private AutomaticTrainingController automaticTrainingController;
    private InteractiveTrainingController interactiveTrainingController;
    private SharedPreferences preferences;

    // private void alert(String alertMessage) {
    // AlertDialog.Builder builder = new AlertDialog.Builder(this);
    // builder.setMessage(alertMessage);
    // builder.create().show();
    // }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO: Attribute minSdkVersion (3) is lower than the project target
        // API level (10)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.training);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        final AudioEventView audioEventView = (AudioEventView) findViewById(R.id.recordButton);
        final ImageView recordStatusView = (ImageView) findViewById(R.id.recordStatusView);
        audioEventView.setRecordStatusView(recordStatusView);
        // Create AudioEventCollector and ask it to pass audio events to the
        // AudioEventView.
        final AudioEventCollector audioEventCollector = new AudioEventCollector(audioEventView);
        // AudioEventCollector provides history of events to be displayed in
        // AudioEventView.
        audioEventView.setAudioEventHistory(audioEventCollector);

        final TrainingApplication application = (TrainingApplication) getApplication();
        final ControllerFactory controllerFactory = application.getControllerFactory();

        // Controllers need to pass audio events to the audioEventCollector. The
        // collector will pass them further to the audioEventView.
        automaticTrainingController =
            controllerFactory.createAutomaticTrainingController(audioEventCollector);
        interactiveTrainingController =
            controllerFactory.createInteractiveTrainingController(audioEventCollector);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Get notification when preferences change. Preferences determine which
        // training controller should be used.
        preferences.registerOnSharedPreferenceChangeListener(this);

        // The whole area in which audio events are plotted is a record button.
        recordButton = audioEventView;
        replayButton = (ImageButton) findViewById(R.id.replayButton);

        horizontalDividerView = findViewById(R.id.horizontalDividerView);

        configureButtons();
        configureActiveSession();
    }

    private void configureButtons() {
        // Buttons control only interactive training session (they are hidden
        // during automatic training session).
        recordButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    interactiveTrainingController.record();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    interactiveTrainingController.play();
                }
                return false;
            }
        });

        replayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg) {
                if (replayButton.isEnabled()) {
                    interactiveTrainingController.play();
                }
            }
        });
    }

    /**
     * Based on a training mode select by the user, determines which controller
     * should be active and configures controls needed in the select mode.
     */
    private void configureActiveSession() {
        final boolean speechDetectionMode =
            this.preferences.getBoolean("speechDetectionMode", true);
        if (speechDetectionMode) {
            activeTrainingController = automaticTrainingController;
        } else {
            activeTrainingController = interactiveTrainingController;
        }
        configureControlsAccordingToMode(speechDetectionMode);
    }

    private void configureControlsAccordingToMode(boolean speechDetectionMode) {
        if (speechDetectionMode) {
            recordButton.setEnabled(false);
            replayButton.setVisibility(View.GONE);
            horizontalDividerView.setVisibility(View.GONE);
        } else {
            recordButton.setEnabled(true);
            replayButton.setVisibility(View.VISIBLE);
            horizontalDividerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeTrainingController.startTraining();
    }

    @Override
    protected void onPause() {
        super.onPause();
        activeTrainingController.stopTraining();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Training can be safely stopped even if it is not started.
        activeTrainingController.stopTraining();
        configureActiveSession();
    }
    /**
     * Display an activity select by the user in the options menu.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settingsMenuItem:
                startActivity(new Intent(this, TrainingPreferenceActivity.class));
                break;
            case R.id.helpMenuItem:
                final Intent helpIntent = new Intent(this, FileViewerActivity.class);
                helpIntent.putExtra(FileViewerActivity.FILE_TO_DISPLAY, "help.html");
                helpIntent.putExtra(FileViewerActivity.WINDOW_TITLE_SUFFIX,
                        getString(R.string.helpTitleSuffix));
                startActivity(helpIntent);
                break;
            case R.id.aboutMenuItem:
                final Intent aboutIntent = new Intent(this, FileViewerActivity.class);
                aboutIntent.putExtra(FileViewerActivity.FILE_TO_DISPLAY, "about.html");
                aboutIntent.putExtra(FileViewerActivity.WINDOW_TITLE_SUFFIX,
                        getString(R.string.aboutTitleSuffix));
                startActivity(aboutIntent);
                break;

        }
        return true;
    }
}
