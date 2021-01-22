


# Java Sample Project

This project shows how to use the Soul Machines Android SDK and pull the library into your own projects. It also shows some of the basic SDK functionality for reference.

# Project Setup
 * Open/import this project in Android Studio as a gradle project.
 * Build the project and it should now download all the dependencies.


# Importing the library
 **Add the maven repository**  
To import the library into your own project, add the following entries to the `app/build.gradle` file and remember to replace with your provided keys
```
 repositories {       
	 maven {    
          url "https://sm-maven-repo-bucket.s3.amazonaws.com"
    }    
}
```
  **Import the library**  
  Add the following dependencies to the `app/build.gradle`
```
 dependencies {        
	 implementation 'com.soulmachines.android:smsdk-core:1.0.0'    
}
```

# Create and connect the Scene
 ## Using View Containers

 * Create **android.view.ViewGroup** container views for the remote persona view (required) and the local video view (optional) on your layout xml where the Scene will be rendered  
 e.g. *activity_main.xml*
 ```
 <FrameLayout      
    android:id="@+id/fullscreenPersonaView"      
    android:layout_width="0dp"      
    android:layout_height="0dp"      
    app:layout_constraintBottom_toBottomOf="parent"      
    app:layout_constraintLeft_toLeftOf="parent"      
    app:layout_constraintRight_toRightOf="parent"      
    app:layout_constraintTop_toTopOf="parent" />
    
<FrameLayout      
    android:id="@+id/pipLocalVideoView"      
    android:layout_width="120dp"      
    android:layout_height="120dp"      
    app:layout_constraintTop_toTopOf="parent"      
    app:layout_constraintLeft_toLeftOf="parent"      
    android:layout_marginLeft="16dp"      
    android:layout_marginTop="16dp" />      
 ```

 * Create a **Scene** object and specify the required **UserMedia** and then set the views on the Scene where you want to render the video feeds. The 2nd parameter (local video view) is optional and can be specified as null.
 ```
 scene = new SceneImpl(this, UserMedia.MicrophoneAndCamera);
 scene.setViews(binding.fullscreenPersonaView, binding.pipLocalVideoView);
 ```

 ## Using a Custom Layout
 * Create a custom layout xml where the Scene video feeds will be rendered. Ensure it has the following child views with the following predefined ids: ***@id/remote_video_view*** and ***@id/local_video_view*** of the type ***org.webrtc.SurfaceViewRenderer***.  
 e.g. *custom_scene_layout.xml*

```
<?xml version="1.0" encoding="utf-8"?> 
<androidx.constraintlayout.widget.ConstraintLayout      
    xmlns:android="http://schemas.android.com/apk/res/android"      
    xmlns:app="http://schemas.android.com/apk/res-auto"      
    xmlns:tools="http://schemas.android.com/tools"      
    android:layout_width="match_parent"      
    android:layout_height="match_parent"      
    android:background="@android:color/black">      
      
    <org.webrtc.SurfaceViewRenderer      
        android:id="@id/remote_video_view"      
        android:layout_width="0dp"      
        android:layout_height="0dp"      
        app:layout_constraintBottom_toBottomOf="parent"      
        app:layout_constraintLeft_toLeftOf="parent"      
        app:layout_constraintRight_toRightOf="parent"      
        app:layout_constraintTop_toTopOf="parent"      
        />      
      
    <org.webrtc.SurfaceViewRenderer      
        android:id="@id/local_video_view"      
        android:layout_width="120dp"      
        android:layout_height="120dp"      
        app:layout_constraintBottom_toBottomOf="parent"      
        app:layout_constraintLeft_toLeftOf="parent"      
		android:layout_marginLeft="16dp"
		android:layout_marginBottom="24dp" /> 
</androidx.constraintlayout.widget.ConstraintLayout> 
```

* Include this layout or embed directly to your Activity's layout. e.g. In your activity's layout file ``` <include android:id="@+id/scene" layout="@layout/custom_scene_layout"/> ```

 * Create a **Scene** object and specify the required **UserMedia**  
and then set the views on the Scene but use the instance of the custom layout you've defined.
 ```
 scene = new SceneImpl(this, UserMedia.MicrophoneAndCamera);
 scene.setViews(binding.scene, binding.scene);
 ```
 *In the snippet above, it uses the same custom layout for both the remote and local video feeds, but you can specify a separate one for each as long as you use the correct predefined id for the corresponding child video view*

 ## Connect to a Digital Human (DH) server using a valid web-socket URL and a valid JWT token
 ```
 scene.connect(
    url = "wss://dh.soulmachines.cloud",      
    accessToken = "JWT_ACCESS_TOKEN");
 ```

 ## Connection Result  
On the provided API (e.g. **Scene** and **Persona**), all the asynchronous method calls provide a way such that you can subscribe to the result (whether it was successful or resulted in an error). These methods will return a **Completable/Cancellable** result from which you can subscribe to the result by passing in a **Completion** callback. This interface accepts a generic type parameter that determines the type of the response for a successful result.

Here's an example of a subscription to the scene connection result:

```
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
```

# Register event listeners on the Scene

The **Scene** and **Persona** api also provides a way to register event listeners that might be necessary to interact with the digital human. For these event listeners, the pattern is **add{Type}EventListener** and **remove{Type}EventListener***. For both these methods, a **{Type}EventListener** implementation is passed as a parameter.

Here's an example showing a listener for a disconnection event for the Scene:
```
scene.addDisconnectedEventListener(reason -> runOnUiThread(() -> onDisconnectedUI(reason)));
```


 # Scene Messages
One way to interact with a *Digital Human* is achieved through *Scene Messaging*. This part of the **Scene#addSceneMessageListener** api allows you to register a listener for when these Scene messages are received. To register a message listener, create an instance of a **com.soulmachines.android.smsdk.core.scene.message.SceneMessageListener** or alternatively an instance of the adaptor class **com.soulmachines.android.smsdk.core.scene.message.SceneMessageListenerAdaptor** and only override the specific *Scene Message* you are interested with.
Here is an example using the SceneMessageListener:
```
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
```


 # Persona API
 A **Persona** instance is the api to use to interact with a *Digital Human*. After a successful connection to a scene and the initial 'state' is established, a **Persona** instance can be obtained from the **Scene#getPersonas()** api.

Here 's a documentation of what methods are available for the Persona and what they can do: ***TODO: Link to the class documentation of the Persona***

An example of usages of the Persona API (see MainActivity#changeCameraView for an example):

```
// make the persona look to the left
if(!scene.getPersonas().isEmpty()) {
    Persona persona = scene.getPersonas().get(0);
    showToastMessage("Changing camera view to the " + direction.toString());
    Log.i(TAG, "CameraView: " + direction.toString());
    persona.animateToNamedCameraWithOrbitPan(getNamedCameraAnimationParam(direction));
}
```