package in.shreybanga.DownloadAccelerator;

import java.io.File;

import in.shreybanga.DownloadAccelerator.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

public class PreferencesEditor extends PreferenceActivity {
	SharedPreferences.Editor editor;

	/*
	 * Validates the Threads field
	 */
	private class onThreadsChangeListener implements Preference.OnPreferenceChangeListener {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			boolean ok = false;
			try {
				int newThreads = Integer.parseInt((String)newValue);
				ok = (newThreads >= 1 && newThreads <= App.MAX_THREADS_ALLOWED);
			} catch(NumberFormatException e) {
			}
			if(!ok) {
				Toast.makeText(PreferencesEditor.this, 
					"Only upto " + App.MAX_THREADS_ALLOWED + " threads are allowed", Toast.LENGTH_SHORT).show();
			}
			return ok;
		}
	}

	/*
	 * Validates the Save To field
	 */
	private class onSaveToChangeListener implements Preference.OnPreferenceChangeListener {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			File file = new File((String)newValue);
			boolean ok = (file.isDirectory() || file.mkdirs());
			if(!ok) {
				Toast.makeText(PreferencesEditor.this,
					"Cannot save to this location", Toast.LENGTH_SHORT).show();
			}
			return ok;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		PreferenceScreen ps = getPreferenceScreen();
		ps.findPreference(App.PREF_THREADS).setOnPreferenceChangeListener(new onThreadsChangeListener());
		ps.findPreference(App.PREF_SAVE_TO).setOnPreferenceChangeListener(new onSaveToChangeListener());

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		Log.i(App.TAG, pref.getString("prefSaveTo", "Default Save To"));
		Log.i(App.TAG, pref.getString("prefThreads", "Default Threads"));
		Log.i(App.TAG, "" + pref.getBoolean("prefAutoStart", false));
	}
}
