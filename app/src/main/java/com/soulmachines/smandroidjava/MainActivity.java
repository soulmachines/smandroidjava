// Copyright 2022 Soul Machines Ltd

package com.soulmachines.smandroidjava;

import static com.soulmachines.android.smsdk.core.UserMedia.MicrophoneAndCamera;
import static java.lang.Math.abs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.soulmachines.android.smsdk.core.SessionInfo;
import com.soulmachines.android.smsdk.core.UserMedia;
import com.soulmachines.android.smsdk.core.async.Completion;
import com.soulmachines.android.smsdk.core.async.CompletionError;
import com.soulmachines.android.smsdk.core.scene.AudioSourceType;
import com.soulmachines.android.smsdk.core.scene.Content;
import com.soulmachines.android.smsdk.core.scene.FeatureFlags;
import com.soulmachines.android.smsdk.core.scene.NamedCameraAnimationParam;
import com.soulmachines.android.smsdk.core.scene.Persona;
import com.soulmachines.android.smsdk.core.scene.Rect;
import com.soulmachines.android.smsdk.core.scene.RetryOptions;
import com.soulmachines.android.smsdk.core.scene.Scene;
import com.soulmachines.android.smsdk.core.scene.SceneFactory;
import com.soulmachines.android.smsdk.core.scene.message.SceneEventMessage;
import com.soulmachines.android.smsdk.core.scene.message.SceneMessageListener;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.ConversationResultEventBody;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.RecognizeResultsEventBody;
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.StateEventBody;
import com.soulmachines.smandroidjava.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import kotlin.Unit;


public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private static String PERMISSION_DONT_ASK_AGAIN_FLAG = "PERMISSION_DONT_ASK_AGAIN_FLAG";
    private static int PERMISSION_REQUEST_UPDATE_USER_MEDIA = 101;

    private boolean micEnabled = true;

    private ActivityMainBinding binding;

    private SharedPreferences preferences;

    private Scene scene = null;

    private Persona persona = null;

    private boolean showContentClicked = false;

    private boolean continueAndDontAskPermissionAgain = false;

    private static Random ran = new Random();

    enum CameraViewDirection {
        Left,
        Center,
        Right
    }

    private UserMedia userMedia = UserMedia.None;
    private UserMedia requestedUserMedia = UserMedia.None;

    //region SpeechRecognizer Usage Example (Mute Button Implementation using SpeechRecognizer - requires MICROPHONE Permission)

    private void toggleMicUsingSpeechRecognizer() {
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
                            binding.microphoneToggle.setSelected(true);
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
                            binding.microphoneToggle.setSelected(false);
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

    //endregion SpeechRecognizer Usage Example (Mute Button Implementation using SpeechRecognizer - requires MICROPHONE Permission)

    //region Scene Usage Example (Change Camera View)

    private void setupChangeCameraViewButtons() {
        binding.lookToTheLeftButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Left));

        binding.lookToTheCenterButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Center));

        binding.lookToTheRightButton.setOnClickListener(v -> changeCameraView(CameraViewDirection.Right));
    }

    private void changeCameraView(final CameraViewDirection direction) {
        if(persona != null) {
            showToastMessage("Changing camera view to the " + direction.toString());
            Log.i(TAG, "CameraView: " + direction.toString());
            persona.animateToNamedCameraWithOrbitPan(getNamedCameraAnimationParam(direction));
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

        continueAndDontAskPermissionAgain = savedInstanceState != null ? savedInstanceState.getBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG) : false;

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));
        goFullScreen();

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.connectButton.setOnClickListener(v -> connect());

        binding.settingsButton.setOnClickListener(v -> openSettingsPage());

        binding.disconnectButton.setOnClickListener(v -> disconnect());

        binding.showContentButton.setOnClickListener(v -> toggleContent());

        scene = SceneFactory.create(this, userMedia);
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

        scene.addPersonaReadyListener(p -> {
            persona = p;
        });

        resetViewUI();

        setupVideoMicToggleButtons();

        // Determine if the SDK is controlling the camera. If this is true then we disable camera controlling buttons
        boolean isCameraControlledBySDK = scene.getFeatures().isFeatureEnabled(FeatureFlags.UI_SDK_CAMERA_CONTROL);
        if (isCameraControlledBySDK) {
            binding.lookToTheLeftButton.setEnabled(false);
            binding.lookToTheRightButton.setEnabled(false);
            binding.lookToTheCenterButton.setEnabled(false);
        } else {
            setupChangeCameraViewButtons();
        }
    }

    private void openSettingsPage() {
        //display the Preference screen and ask them and ask them to populate the required
        //settings before we can let them connect
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        continueAndDontAskPermissionAgain = savedInstanceState.getBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG, continueAndDontAskPermissionAgain);
    }

    //endregion Setup Activity UI

    //region Scene Connection Usage Example
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
                new Completion<SessionInfo>() {
                    @Override
                    public void onSuccess(SessionInfo sessionInfo) {
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

    private void toggleContent() {
        showContentClicked = !showContentClicked;
        binding.contentView.setVisibility(View.GONE);
        if (!showContentClicked) {
            scene.getContentAwareness().removeAllContent();
            scene.getContentAwareness().syncContentAwareness();
        }

        binding.showContentButton.setImageResource(showContentClicked ? R.drawable.ic_nocontent : R.drawable.ic_content);
    }

    //endregion Scene Connection Usage Example

    //region Video/Audio Toggle Example

    private void setupVideoMicToggleButtons() {
        //toggle the state
        binding.microphoneToggle.setOnClickListener(it -> {
            binding.microphoneToggle.setSelected(!binding.microphoneToggle.isSelected());
            //fire the change event
            videoAudioActiveChanged();
        });

        binding.videoToggle.setOnClickListener(it -> {
            binding.videoToggle.setSelected(!binding.videoToggle.isSelected());
            //fire the change event
            videoAudioActiveChanged();
        });

    }

    private void videoAudioActiveChanged() {
        final boolean isMicEnabled = binding.microphoneToggle.isSelected();
        final boolean isVideoEnabled = binding.videoToggle.isSelected();

        final UserMedia requestedUserMedia;
        if(isMicEnabled && isVideoEnabled) {
            requestedUserMedia = MicrophoneAndCamera;
        } else if(!isMicEnabled && isVideoEnabled) {
            requestedUserMedia = UserMedia.Camera;
        } else if(isMicEnabled && !isVideoEnabled) {
            requestedUserMedia = UserMedia.Microphone;
        } else {
            requestedUserMedia = UserMedia.None;
        }
        updateUserMediaWithPermission(requestedUserMedia);

    }

    private void onPermissionGrantedUpdateUserMedia() {
        Log.d(TAG, "Applying UserMedia:" + userMedia);
        if(scene != null) {
            scene.updateUserMedia(this.userMedia);
        }
        //ensure state of buttons and views are in sync with active userMedia
        binding.microphoneToggle.setSelected(this.userMedia.getHasAudio());
        binding.videoToggle.setSelected(this.userMedia.getHasVideo());
        binding.pipLocalVideoView.setVisibility( this.userMedia.getHasVideo() ? View.VISIBLE : View.GONE);
    }

    //endregion Video/Audio Toggle Example
    
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
    private void updateUserMediaWithPermission(UserMedia requestedUserMedia) {
        this.requestedUserMedia = requestedUserMedia;
        switch (requestedUserMedia) {
             case MicrophoneAndCamera : {
                 requestPermissionIfNeededForCameraAndMic();
                 break;
             }
            case Camera : {
                requestPermissionIfNeededFor(Manifest.permission.CAMERA);
                break;
            }
            case Microphone : {
                requestPermissionIfNeededFor(Manifest.permission.RECORD_AUDIO);
                break;
            }
            default : {
                applyAllowedUserMedia();
                break;
            }
        }
    }

    private void applyAllowedUserMedia() {
        this.userMedia = requestUserMedia(this.requestedUserMedia);
        onPermissionGrantedUpdateUserMedia();
    }

    private void requestPermissionIfNeededForCameraAndMic() {

        final String[] permissionsRequired = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) + ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            boolean shouldShowRecordAudioPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO);
            boolean shouldShowCameraPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
            if (shouldShowRecordAudioPermission || shouldShowCameraPermission) {
                //if we have to show the permissions screen then we have to override this flag
                // so it is skipped
                continueAndDontAskPermissionAgain = false;
                showExplanation("Permission Required", "You need to enable permissions.", permissionsRequired, PERMISSION_REQUEST_UPDATE_USER_MEDIA);
            } else {
                requestPermissions(permissionsRequired, PERMISSION_REQUEST_UPDATE_USER_MEDIA);
            }
        } else {
            applyAllowedUserMedia();
        }
    }

    private void requestPermissionIfNeededFor(String permissionRequired) {
        if (ContextCompat.checkSelfPermission(this, permissionRequired) != PackageManager.PERMISSION_GRANTED) {
            boolean shouldShowPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, permissionRequired);
            if (shouldShowPermission) {
                //if we have to show the permissions screen then we have to override this flag
                // so it is skipped
                continueAndDontAskPermissionAgain = false;
                showExplanation("Permission Required", "You need to enable permissions.", new String[]{permissionRequired}, PERMISSION_REQUEST_UPDATE_USER_MEDIA);
            } else {
                requestPermissions(new String[]{permissionRequired}, PERMISSION_REQUEST_UPDATE_USER_MEDIA);
            }
        } else {
            applyAllowedUserMedia();
        }
    }

    private UserMedia requestUserMedia(UserMedia newUserMedia) {
        //none allowed by default
        if(newUserMedia == UserMedia.None) return newUserMedia;
        //check for the permissions and request if necessary
        final String[] missingPermissions = getMissingPermissions();
        boolean allowAudio = !Arrays.stream(missingPermissions).anyMatch("android.permission.RECORD_AUDIO"::equals);
        boolean allowVideo = !Arrays.stream(missingPermissions).anyMatch("android.permission.CAMERA"::equals);

        //if the usermedia matches the allowed flags or if everything is granted, then return the requested userMedia
        //otherwise just return the original value
        if((allowAudio && allowVideo) || (newUserMedia.getHasAudio() == allowAudio && newUserMedia.getHasVideo() == allowVideo)) {
            return newUserMedia;
        }
        return this.userMedia;
    }

    private void showExplanation(String title,
                                 String message,
                                String[] permissions,
                                int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    requestPermissions(permissions, permissionRequestCode);
                });
        builder.create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == PERMISSION_REQUEST_UPDATE_USER_MEDIA) {
            UserMedia applicableUserMedia = requestUserMedia(this.requestedUserMedia);
            if(this.requestedUserMedia == applicableUserMedia) {
                this.userMedia = applicableUserMedia;
                onPermissionGrantedUpdateUserMedia();
            } else {
                if(continueAndDontAskPermissionAgain) {
                    this.userMedia = applicableUserMedia;
                    onPermissionGrantedUpdateUserMedia();
                } else {
                    //display an alert saying some permissions are not enabled and if they wish to continue
                    new AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setTitle("Missing Permissions")
                            //.setMessage(Html.fromHtml(getString(R.string.permissions_settings_message, applicableUserMedia), Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH))
                            .setMessage(Html.fromHtml(getString(R.string.permissions_settings_message, this.requestedUserMedia, applicableUserMedia), Html.FROM_HTML_MODE_LEGACY))
                            .setPositiveButton(R.string.yes, (dialog1, which) -> {
                                dialog1.cancel();
                                continueAndDontAskPermissionAgain = false;
                                this.userMedia = applicableUserMedia;
                                onPermissionGrantedUpdateUserMedia();
                            })
                            .setNegativeButton(R.string.permissions_setting, (dialog2, which) -> {
                            // User doesn't want to give the permissions.
                                this.userMedia = applicableUserMedia;
                                onPermissionGrantedUpdateUserMedia();
                                dialog2.cancel();
                                continueAndDontAskPermissionAgain = false;
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNeutralButton(R.string.permissions_setting_continue, (dialogneutral, which) -> {
                                dialogneutral.cancel();
                                continueAndDontAskPermissionAgain = true;
                                this.userMedia = applicableUserMedia;
                                onPermissionGrantedUpdateUserMedia();
                            }).show();
                }
            }
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

        binding.showContentButton.setVisibility(View.GONE);
        binding.showContentButton.setEnabled(false);

        binding.microphoneToggle.setVisibility(View.GONE);
        binding.videoToggle.setVisibility(View.GONE);

        binding.cameraViewsContainer.setVisibility(View.INVISIBLE);

        binding.contentView.setVisibility(View.GONE);
    }

    private void onDisconnectingUI() {
        binding.disconnectButtonContainer.setVisibility(View.VISIBLE);
        binding.disconnectButton.setEnabled( false);
        binding.microphoneToggle.setVisibility(View.GONE);
        binding.videoToggle.setVisibility(View.GONE);
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

        // Determine if Content Awareness is supported. See the Content Awareness section for more information on Content Awareness.
        boolean isContentAwarenessSupported = scene.getFeatures().isFeatureEnabled(FeatureFlags.UI_CONTENT_AWARENESS);
        if (isContentAwarenessSupported) {
            binding.showContentButton.setVisibility(View.VISIBLE);
            binding.showContentButton.setEnabled(true);
        }

        binding.microphoneToggle.setVisibility(View.VISIBLE);
        binding.videoToggle.setVisibility(View.VISIBLE);

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

    private int PixelToDip(int pixel) {
        Resources r = getResources();
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, pixel, r.getDisplayMetrics());
    }

    private void showContentView(int rawX, int rawY, int randomW, int randomH) {
        binding.contentView.setVisibility(View.VISIBLE);
        binding.contentView.setBackgroundColor(Color.RED);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(randomW, randomH);
        params.leftMargin = PixelToDip(rawX - (randomW/2));
        params.topMargin = PixelToDip(rawY - (randomH/2));
        binding.contentView.setLayoutParams(params);
        binding.contentView.setWidth(PixelToDip(randomW));
        binding.contentView.setHeight(PixelToDip(randomH));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_UP:
                if (showContentClicked) {
                    int randomW = Math.max(100, ran.nextInt(200));
                    int randomH = Math.max(100, ran.nextInt(200));

                    showContentView((int)e.getRawX(), (int)e.getRawY(), randomW, randomH);

                    Rect bounds = new Rect((int)e.getRawX(), (int)e.getRawY(),(int)e.getRawX()+randomW,(int)e.getRawY()+randomH);
                    Content content = new ContentImpl(bounds);
                    scene.getContentAwareness().addContent(content);
                    scene.getContentAwareness().syncContentAwareness();
                }
                break;
        }
        return super.onTouchEvent(e);
    }

    //endregion UI Behaviour
}