/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.farmerbb.taskbar.MainActivity;
import com.farmerbb.taskbar.service.NotificationService;
import com.farmerbb.taskbar.util.U;

public class SettingsFragment extends PreferenceFragment {

    boolean finishedLoadingPrefs;
    boolean showReminderToast = false;
    boolean restartNotificationService = false;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Remove dividers
        View rootView = getView();
        if(rootView != null) {
            ListView list = (ListView) rootView.findViewById(android.R.id.list);
            if(list != null) list.setDivider(null);
        }

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // On smaller-screened devices, set "Grid" as the default start menu layout
        SharedPreferences pref = U.getSharedPreferences(getActivity());
        if(getActivity().getApplicationContext().getResources().getConfiguration().smallestScreenWidthDp < 600
                && pref.getString("start_menu_layout", "null").equals("null")) {
            pref.edit().putString("start_menu_layout", "grid").apply();
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            if(!pref.getBoolean("freeform_hack_override", false)) {
                pref.edit()
                        .putBoolean("freeform_hack", U.hasFreeformSupport(getActivity()))
                        .putBoolean("freeform_hack_override", true)
                        .apply();
            } else if(!U.hasFreeformSupport(getActivity())) {
                pref.edit().putBoolean("freeform_hack", false).apply();

                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent("com.farmerbb.taskbar.FINISH_FREEFORM_ACTIVITY"));
            }
        }
    }

    private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if(preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

                if(finishedLoadingPrefs && preference.getKey().equals("theme")) {
                    // Restart MainActivity
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("theme_change", true);
                    startActivity(intent);
                    getActivity().overridePendingTransition(0, 0);
                }

            } else if(!(preference instanceof CheckBoxPreference)) {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }

            if(finishedLoadingPrefs) U.restartTaskbar(getActivity());

            return true;
        }
    };

    void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        if(!(preference instanceof CheckBoxPreference))
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Override default Android "up" behavior to instead mimic the back button
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if(restartNotificationService) {
            restartNotificationService = false;

            if(U.isServiceRunning(getActivity(), NotificationService.class)) {
                SharedPreferences pref = U.getSharedPreferences(getActivity());
                pref.edit().putBoolean("is_restarting", true).apply();

                Intent intent = new Intent(getActivity(), NotificationService.class);
                getActivity().stopService(intent);
                getActivity().startService(intent);
            }
        }
    }
}