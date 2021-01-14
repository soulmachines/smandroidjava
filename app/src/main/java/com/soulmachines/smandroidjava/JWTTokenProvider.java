package com.soulmachines.smandroidjava;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class JWTTokenProvider {

    private SharedPreferences preferences;

    public JWTTokenProvider(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }


    public void getJWTToken(JWTSource.OnSuccess success, JWTSource.OnError error) {
        getJwtSource().getJwt(success, error);
    }

    // identify the strategy/implementation to use based on configuration
    private JWTSource getJwtSource()  {
        String accessToken = preferences.getString(ConfigurationFragment.JWT_TOKEN, "");
        String selfSigningKeyName = preferences.getString(ConfigurationFragment.KEY_NAME, "");
        String selfSigningPrivateKey = preferences.getString(ConfigurationFragment.PRIVATE_KEY, "");

        Boolean useOrchServer = preferences.getBoolean(ConfigurationFragment.USE_ORCHESTRATION_SERVER, false);
        String orchestrationServerURL = preferences.getString(ConfigurationFragment.ORCHESTRATION_SERVER_URL, "");

        Boolean useExistingToken = preferences.getBoolean(ConfigurationFragment.USE_EXISTING_JWT_TOKEN, false);

        if(useExistingToken) {
            return new JWTSource.PregeneratedJwtSource(accessToken);
        } else if(selfSigningKeyName != null && selfSigningKeyName.trim().length() > 0  &&
                selfSigningPrivateKey != null && selfSigningPrivateKey.trim().length() > 0) {
            return new JWTSource.SelfSigned(
                    selfSigningPrivateKey,
                    selfSigningKeyName,
            useOrchServer ? orchestrationServerURL: null);
        } else {
            throw new IllegalStateException("The required properties on the Settings Screen must be set.");
        }
    }
}