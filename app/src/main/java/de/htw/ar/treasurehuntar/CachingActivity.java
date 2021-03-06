package de.htw.ar.treasurehuntar;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;
import com.google.android.glass.content.Intents;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.wikitude.architect.ArchitectView.ArchitectUrlListener;
import com.wikitude.architect.ArchitectView.SensorAccuracyChangeListener;

import java.io.File;

/**
 * Schätze verstecken
 */
public class CachingActivity extends AbstractArchitectActivity {

    private static final int TAKE_PICTURE_REQUEST = 1;
    private static final int TAKE_AUDIO_REQUEST = 2;

    private final static String POST_CACHE_URL = "http://vegapunk.de:9999/cache";

    /**
     * extras key for activity title, usually static and set in Manifest.xml
     */
    protected static final String EXTRAS_KEY_ACTIVITY_TITLE_STRING = "activityTitle";

    /**
     * extras key for architect-url to load, usually already known upfront, can be relative folder to assets (myWorld.html --> assets/myWorld.html is loaded) or web-url ("http://myserver.com/myWorld.html"). Note that argument passing is only possible via web-url
     */
    protected static final String EXTRAS_KEY_ACTIVITY_ARCHITECT_WORLD_URL = "activityArchitectWorldUrl";

    /**
     * last time the calibration toast was shown, this avoids too many toast shown when compass needs calibration
     */
    private long lastCalibrationToastShownTimeMillis = System
            .currentTimeMillis();

    private GestureDetector mGestureDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mGestureDetector = createGestureDetector(this);

        super.onCreate(savedInstanceState);
    }

    @Override
    public String getARchitectWorldPath() {
        return getIntent().getExtras().getString(
                EXTRAS_KEY_ACTIVITY_ARCHITECT_WORLD_URL);
    }

    @Override
    public String getActivityTitle() {
        return (getIntent().getExtras() != null && getIntent().getExtras().get(
                EXTRAS_KEY_ACTIVITY_TITLE_STRING) != null) ? getIntent()
                .getExtras().getString(EXTRAS_KEY_ACTIVITY_TITLE_STRING)
                : "Test-World";
    }

    @Override
    public int getContentViewId() {
        return R.layout.main_layout;
    }

    @Override
    public int getArchitectViewId() {
        return R.id.architectView;
    }

    @Override
    public SensorAccuracyChangeListener getSensorAccuracyListener() {
        return new SensorAccuracyChangeListener() {
            @Override
            public void onCompassAccuracyChanged(int accuracy) {
                /* UNRELIABLE = 0, LOW = 1, MEDIUM = 2, HIGH = 3 */
                if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                        && isFinishing() && System.currentTimeMillis()
                        - CachingActivity.this.lastCalibrationToastShownTimeMillis
                        > 5 * 1000) {
                    Toast.makeText(CachingActivity.this,
                            R.string.compass_accuracy_low, Toast.LENGTH_LONG)
                            .show();
                    CachingActivity.this.lastCalibrationToastShownTimeMillis = System
                            .currentTimeMillis();
                }
            }
        };
    }

    @Override
    public ArchitectUrlListener getUrlListener() {
        return new ArchitectUrlListener() {

            @Override
            public boolean urlWasInvoked(String uriString) {
                // by default: no action applied when url was invoked
                return false;
            }
        };
    }

    @Override
    protected GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        //Create a base listener for generic gestures
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    Log.i("gesture", "Tap");
                    takePicture();
                    return true;
                }
                return false;
            }
        });

        return gestureDetector;
    }

    private void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
    }

    /**
     * Send generic motion events to the gesture detector
     *
     * @param event the gesture event
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mGestureDetector != null && mGestureDetector.onMotionEvent(event);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                // First take a picure
                case TAKE_PICTURE_REQUEST:
                    Log.i("result", "apicture request");
                    String thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);
                    Intent intent = new Intent(CachingActivity.this, AudioRecorder.class);
                    intent.putExtra("imgPath", thumbnailPath);
                    startActivityForResult(intent, TAKE_AUDIO_REQUEST);
                    break;

                // Record audio and send to server
                case TAKE_AUDIO_REQUEST:
                    Log.i("result", "audio request");
                    String audioPath = data.getStringExtra("audioPath");
                    String imgPath = data.getStringExtra("imgPath");
                    new SendFileCache().execute(imgPath, audioPath);
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    /**
     * Send file to server
     */
    class SendFileCache extends AsyncTask<String, Void, String> {

        private String imagePath;
        private String audioPath;

        @Override
        protected String doInBackground(String... paths) {
            imagePath = paths[0];
            audioPath = paths[1];

            try {
                MultipartUtility multipart = new MultipartUtility(POST_CACHE_URL, "UTF-8");

                multipart.addFormField("description", "Treasure-"); // TODO: Voice Recognition Text einfuegen
                multipart.addFormField("latitude", String.valueOf(lastKnownLocation.getLatitude()));
                multipart.addFormField("longitude", String.valueOf(lastKnownLocation.getLongitude()));
                multipart.addFormField("altitude", String.valueOf(lastKnownLocation.getAltitude()));

                multipart.addFilePart("image", new File(imagePath));
                multipart.addFilePart("audio", new File(audioPath));

                return multipart.getResponse();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }


        @Override
        protected void onPostExecute(String result) {
            Log.i("SendFileChace ", "Post execute " + result);
            if (result == null) {
                Toast.makeText(CachingActivity.this, R.string.upload_fail, Toast.LENGTH_SHORT).show();
            } else if (result.equals("200")) {
                Toast.makeText(CachingActivity.this, R.string.upload_ok, Toast.LENGTH_SHORT).show();
            }
        }

    }
}

