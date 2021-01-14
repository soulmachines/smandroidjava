package com.soulmachines.smandroidjava;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigurationFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PRIVATE_KEY = "connectionConfigPrivateKey";
    public static final String KEY_NAME = "connectionConfigKeyName";
    public static final String CONNECTION_URL = "connectionUrl";
    public static final String USE_EXISTING_JWT_TOKEN = "useExistingJWTToken";
    public static final String JWT_TOKEN = "connectionConfigJWT";
    public static final String ORCHESTRATION_SERVER_URL = "orchestrationServerURL";
    public static final String USE_ORCHESTRATION_SERVER = "useOrchestrationServer";

    private SettingsActivity activity;
    private SharedPreferences sharedPreferences;

    private Map<String, String> existingSummary = new HashMap<>();

    private List<Preference> selfSignedStrategyRelatedPrefs = new ArrayList<>();
    private Set<String> selfSignedStrategyRelatedPrefsKeys = new HashSet<>();

    public ConfigurationFragment(SettingsActivity activity) {
        super();
        this.activity = activity;
        selfSignedStrategyRelatedPrefsKeys.add(KEY_NAME);
        selfSignedStrategyRelatedPrefsKeys.add(PRIVATE_KEY);
        selfSignedStrategyRelatedPrefsKeys.add(USE_ORCHESTRATION_SERVER);
        selfSignedStrategyRelatedPrefsKeys.add(ORCHESTRATION_SERVER_URL);
    }


    @Override
    public void onResume() {
        super.onResume();

        // we want to watch the preference values' changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        Map<String, ?> preferencesMap = sharedPreferences.getAll();
        // iterate through the preference entries and update their summary if they are an instance of EditTextPreference
        for (Map.Entry<String, ?> preference : preferencesMap.entrySet()) {
            Preference preferenceEntry = findPreference(preference.getKey());
            if (preferenceEntry instanceof EditTextPreference) {
                updateSummary((EditTextPreference)preferenceEntry);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        final Map<String, ?> preferencesMap = sharedPreferences.getAll();

        Preference pref = findPreference(key);
        // and if it's an instance of EditTextPreference class, update its summary
        if (pref instanceof EditTextPreference) {
            updateSummary((EditTextPreference) pref);
        }
    }

    private void updateSummary(EditTextPreference preference) {
        // set the EditTextPreference's summary value to its current text
        String text = preference.getText();
        if(text != null && text.trim().length() > 0) {
            preference.setSummary(text);
        } else {
            preference.setSummary(existingSummary.get(preference.getKey()));
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.configuration, rootKey);

        sharedPreferences = getPreferenceManager().getSharedPreferences();

        //save all existing summary definitions
        Map<String, ?> preferencesMap = sharedPreferences.getAll();
        for (Map.Entry<String, ?> preference: preferencesMap.entrySet()) {
            Preference preferenceEntry = findPreference(preference.getKey());
            if(preferenceEntry != null) {
                if(selfSignedStrategyRelatedPrefsKeys.contains(preferenceEntry.getKey())) {
                    selfSignedStrategyRelatedPrefs.add(preferenceEntry);
                }
                if(preferenceEntry instanceof EditTextPreference) {
                    existingSummary.put(preferenceEntry.getKey(), preferenceEntry.getSummary().toString());
                }

                if(preferenceEntry.getKey().equals(USE_EXISTING_JWT_TOKEN)) {
                    ((SwitchPreferenceCompat) preferenceEntry ).setOnPreferenceChangeListener((preference1, newValue) -> {
                        changeStateOfSelfSignedRelatedProperties(!((Boolean) newValue));
                        return true;
                    });

                }
            }
        }

        //set initial disabled state of the self gen related properties
        Preference useJWTProp = findPreference(USE_EXISTING_JWT_TOKEN);
        changeStateOfSelfSignedRelatedProperties(!((SwitchPreferenceCompat)useJWTProp).isChecked());

        Preference button = getPreferenceManager().findPreference("applyChanges");
        if (button != null) {
           button.setOnPreferenceClickListener(preference -> {
               openMainActivity();
               return true;
           });
        }
    }

    private void changeStateOfSelfSignedRelatedProperties(Boolean isEnabled) {
        for(Preference pref : selfSignedStrategyRelatedPrefs) {
            pref.setEnabled(isEnabled);
        }
    }

    private void openMainActivity() {
        Intent newIntent = new Intent(activity, MainActivity.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(newIntent);
        activity.finish();
    }
}