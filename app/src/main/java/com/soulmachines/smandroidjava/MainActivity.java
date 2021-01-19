package com.soulmachines.smandroidjava;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.soulmachines.android.smsdk.core.Session;
import com.soulmachines.android.smsdk.core.UserMedia;
import com.soulmachines.android.smsdk.core.async.Completion;
import com.soulmachines.android.smsdk.core.async.CompletionError;
import com.soulmachines.android.smsdk.core.scene.AudioSourceType;
import com.soulmachines.android.smsdk.core.scene.NamedCameraAnimationParam;
import com.soulmachines.android.smsdk.core.scene.Persona;
import com.soulmachines.android.smsdk.core.scene.RetryOptions;
import com.soulmachines.android.smsdk.core.scene.Scene;
import com.soulmachines.android.smsdk.core.scene.SceneImpl;
import com.soulmachines.android.smsdk.core.scene.message.SceneEventMessage;
import com.soulmachines.android.smsdk.core.scene.message.SceneMessageListener;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.ConversationResultEventBody;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.RecognizeResultsEventBody;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.StateEventBody;
import com.soulmachines.smandroidjava.databinding.ActivityMainBinding;

import java.util.ArrayList;

import kotlin.Unit;

import static java.lang.Math.abs;


public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private static int PERMISSIONS_REQUEST = 2;

    private boolean micEnabled = true;

    private ActivityMainBinding binding;

    private SharedPreferences preferences;

    private Scene scene = null;

    enum CameraViewDirection {
        Left,
        Center,
        Right
    }


    //region SpeechRecognizer Usage Example (Mute Button Implementation)

    private void setupMicButton() {
        binding.microphone.setOnClickListener(v -> toggleMic());
        binding.microphone.setSelected(micEnabled);

    }

    private void toggleMic() {
        boolean shouldEnableMic = !micEnabled;

        if(scene != null && scene.getSpeechRecognizer() != null) {

            // this example changes the mic mute button state after the async call has succeeded
            if(shouldEnableMic) {
                scene.getSpeechRecognizer().startRecognize(AudioSourceType.Processed).subscribe(new Completion<Unit>() {
                    @Override
                    public void onSuccess(Unit unused) {
                        Log.i(TAG, "SpeechRecognition ON ");
                        runOnUiThread(() -> {
                            micEnabled = true;
                            binding.microphone.setSelected(true);
                        });
                    }

                    @Override
                    public void onError(CompletionError completionError) {
                        Log.w(TAG, "Failed to enable SpeechRecognition.");
                    }
                });

            } else {

                scene.getSpeechRecognizer().stopRecognize().subscribe(new Completion<Unit>() {
                    @Override
                    public void  onError(CompletionError error) {
                        Log.w(TAG, "Failed to turn off SpeechRecognition.");
                    }

                    @Override
                    public void  onSuccess(Unit result) {
                        Log.i(TAG, "SpeechRecognition OFF ");
                        runOnUiThread(() -> {
                            micEnabled = false;
                            binding.microphone.setSelected(false);
                        });
                    }
                });
            }


            // alternatively, you can just call the async method and change state immediately and assume it is successful as shown below
            //        micEnabled = !micEnabled;
            //        if(micEnabled) {
            //            scene.getSpeechRecognizer().startRecognize(AudioSourceType.Processed);
            //        } else {
            //            scene.getSpeechRecognizer().stopRecognize();
            //        }

        }

    }

    //endregion SpeechRecognizer Usage Example (Mute Button Implementation)

    //region Scene Usage Example (Change Camera View)

    private void setupChangeCameraViewButtons() {
        binding.lookToTheLeftButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Left));

        binding.lookToTheCenterButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Center));

        binding.lookToTheRightButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Right));
    }

    private void changeCameraView(final CameraViewDirection direction) {
        if(scene != null) {
            if(!scene.getPersonas().isEmpty()) {
                Persona persona = scene.getPersonas().get(0);
                showToastMessage("Changing camera view to the " + direction.toString());
                Log.i(TAG, "CameraView: " + direction.toString());
                persona.animateToNamedCameraWithOrbitPan(getNamedCameraAnimationParam(direction));
            }
        }
    }

    private NamedCameraAnimationParam getNamedCameraAnimationParam(CameraViewDirection direction)   {
        int scalar = 0;
        if(direction == CameraViewDirection.Left) scalar = 1;
        if(direction == CameraViewDirection.Right) scalar = -1;
        return new NamedCameraAnimationParam("CloseUp",
                1.0f, //1 sec
                10.0f * scalar,
                10f * abs(scalar),
                2f * scalar,
                0f);
    }

    //endregion Scene Usage Example (Change Camera View)



    //region Setup Activity UI
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));
        goFullScreen();

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.connectButton.setOnClickListener(v -> connectRequestingPermissionsIfNeeded());

        binding.settingsButton.setOnClickListener(v -> openSettingsPage());

        binding.disconnectButton.setOnClickListener(v -> disconnect());

        scene = new SceneImpl(this, UserMedia.MicrophoneAndCamera);
        scene.setViews(binding.fullscreenPersonaView, binding.pipLocalVideoView);

        scene.addDisconnectedEventListener(reason -> runOnUiThread(() -> onDisconnectedUI(reason)));


        //example of registering a SceneMessageListener
        scene.addSceneMessageListener(new SceneMessageListener() {
            @Override
            public void onUserTextEvent(String userText) {
                // userText from server received
            }

            @Override
            public void onStateMessage(SceneEventMessage<StateEventBody> sceneEventMessage) {
                //consume the scene `state` message
            }

            @Override
            public void onRecognizeResultsMessage(SceneEventMessage<RecognizeResultsEventBody> sceneEventMessage) {
                //consume the scene `recognizedResults` message
            }

            @Override
            public void onConversationResultMessage(SceneEventMessage<ConversationResultEventBody> sceneEventMessage) {
                //consume the scene `conversationResult` message
            }
        });
        // alternatively, using an adaptor, you can choose to override only one particular message
//        scene.addSceneMessageListener(new SceneMessageListenerAdaptor() {
//            @Override
//            public void onRecognizeResultsMessage(SceneEventMessage<RecognizeResultsEventBody> sceneEventMessage) {
//                super.onRecognizeResultsMessage(sceneEventMessage);
//            }
//        });

        resetViewUI();

        setupMicButton();

        setupChangeCameraViewButtons();

    }

    private void openSettingsPage() {
        //display the Preference screen and ask them and ask them to populate the required
        //settings before we can let them connect
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    //endregion Setup Activity UI

    //region Scene/Session Connection Usage Example
    private void connect() {

        if(!hasRequiredConfiguration()) {
            openSettingsPage();
            return;
        }

        onConnectingUI();

        // Obtain a JWT token and then connect the Scene
        new JWTTokenProvider(this).getJWTToken(
                (JWTSource.OnSuccess) jwtToken -> {
                    String connectionUrl = preferences.getString(ConfigurationFragment.CONNECTION_URL, "");
                    Log.i(TAG, "Connecting to:  `" + connectionUrl + "`");
                    Log.d(TAG, "Using JWT Token `" + jwtToken + "`");
                    //using the obtained JWT token, proceed with connecting the Scene
                    connectScene(connectionUrl, jwtToken);

                }, (JWTSource.OnError) errorMessage -> {
                    Log.e(TAG, errorMessage);
                    displayAlertAndResetUI(
                            getString(R.string.connection_error),
                            getString(R.string.connection_jwt_error_message)
                    );
                });
    }

    private void connectScene(final String connectionUrl, final String jwtToken) {
        if(scene != null) {
            scene.connect(connectionUrl, null, jwtToken, RetryOptions.getDEFAULT()).subscribe(
                new Completion<Session.SessionInfo>() {
                    @Override
                    public void onSuccess(Session.SessionInfo sessionInfo) {
                        runOnUiThread(() -> onConnectedUI());
                    }
                    @Override
                    public void onError(CompletionError completionError) {
                        runOnUiThread(() -> {
                            displayAlertAndResetUI(getString(R.string.connection_error), completionError.getMessage());
                        });
                    }
            });
        }
    }


    private Boolean hasRequiredConfiguration() {
        Boolean useExistingToken = preferences.getBoolean(ConfigurationFragment.USE_EXISTING_JWT_TOKEN, false);
        String url = preferences.getString(ConfigurationFragment.CONNECTION_URL, null);
        String keyName = preferences.getString(ConfigurationFragment.KEY_NAME, null);
        String privateKey = preferences.getString(ConfigurationFragment.PRIVATE_KEY, null);
        String existingToken = preferences.getString(ConfigurationFragment.JWT_TOKEN, null);
        return isNotNullAndNotEmpty(url) && ((useExistingToken && isNotNullAndNotEmpty(existingToken)
                || (isNotNullAndNotEmpty(keyName) && isNotNullAndNotEmpty(privateKey))));
    }

    private boolean isNotNullAndNotEmpty(String str) {
        return str != null && str.trim().length() > 0;
    }

    private void disconnect() {
        onDisconnectingUI();

        if(scene != null) {
            scene.disconnect();
        }
    }
    //endregion Scene/Session Connection Usage Example

    // region Go Fullscreen
    private void goFullScreen() {
        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
    }

    private int getSystemUiVisibility() {
        return View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }
    //endregion Go Fullscreen

    //region Setup Permissions
    private void onPermissionsGranted() {
        connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST) {
            String[]missingPermissions = getMissingPermissions();
            if (missingPermissions.length > 0) {
                // User didn't grant all the permissions. Warn that the application might not work
                // correctly.
                new AlertDialog.Builder(this).setMessage(R.string.missing_permissions_message)
                        .setPositiveButton(R.string.close, (dialog, which) -> {
                            dialog.cancel();
                        })
                        .show();
            } else {
                // All permissions granted.
                onPermissionsGranted();
            }
        }
    }

    private void connectRequestingPermissionsIfNeeded() {
        final String[] missingPermissions = getMissingPermissions();
        if (missingPermissions != null && missingPermissions.length > 0) {
            requestPermissions(missingPermissions, PERMISSIONS_REQUEST);
        } else {
            onPermissionsGranted();
        }
    }

    private String[] getMissingPermissions() {
        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to retrieve permissions.");
            return new String[0];
        }

        if (info.requestedPermissions == null) {
            Log.w(TAG, "No requested permissions.");
            return new String[0];
        }

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
                missingPermissions.add(info.requestedPermissions[i]);
            }
        }
        Log.d(TAG, "Missing permissions: " + missingPermissions);

        return missingPermissions.toArray(new String[missingPermissions.size()]);
    }
    //endregion Setup Permissions

    //region UI Behaviour
    private void resetViewUI() {
        binding.connectButtonContainer.setVisibility(View.VISIBLE);
        binding.connectButton.setEnabled(true);

        binding.disconnectButtonContainer.setVisibility(View.GONE);
        binding.disconnectButton.setEnabled(true);


        binding.settingsButton.setVisibility(View.VISIBLE);
        binding.settingsButton.setEnabled(true);

        binding.microphone.hide();

        binding.cameraViewsContainer.setVisibility(View.INVISIBLE);
    }

    private void onDisconnectingUI() {
        binding.disconnectButtonContainer.setVisibility(View.VISIBLE);
        binding.disconnectButton.setEnabled( false);
        binding.microphone.hide();
    }

    private void onDisconnectedUI(String reason) {
        Toast.makeText(this, "Disconnected ( " + reason + ")", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> finish(), 100);
    }

    private void onConnectingUI() {
        Toast.makeText(this, "Connecting, please wait...", Toast.LENGTH_LONG).show();
        binding.connectButtonContainer.setVisibility(View.VISIBLE);
        binding.connectButton.setEnabled(false);

        binding.settingsButton.setVisibility(View.GONE);
        binding.settingsButton.setEnabled(false);
    }

    private void onConnectedUI() {
        binding.connectButtonContainer.setVisibility(View.GONE);
        binding.disconnectButtonContainer.setVisibility(View.VISIBLE);
        binding.disconnectButton.setEnabled(true);

        binding.settingsButton.setVisibility(View.GONE);

        binding.microphone.show();

        binding.cameraViewsContainer.setVisibility(View.VISIBLE);
    }


    private void displayAlertAndResetUI(String title, String alertMessage) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(alertMessage).setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.cancel();
                    resetViewUI();
                })
                .create().show();
    }

    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    //endregion UI Behaviour

}