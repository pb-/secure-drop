package dev.baecher.securedrop;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

public class DropZoneSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        setTitle("Configure drop zone");

        String dropZoneId = getIntent().getStringExtra("dropZoneId");
        Log.i("settings", "editing drop-zone id " + dropZoneId);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(dropZoneId))
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private String dropZoneId;

        public SettingsFragment(String dropZoneId) {
            this.dropZoneId = dropZoneId;
        }
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getPreferenceManager().getContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

            EditTextPreference name = new EditTextPreference(context);
            name.setKey("drop-zone:" + dropZoneId + ":name");
            name.setDefaultValue("Untitled drop zone");
            name.setDialogTitle("Name");
            name.setDialogMessage("Name to identify the drop zone");
            name.setTitle("Name");
            name.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            screen.addPreference(name);

            EditTextPreference endpoint = new EditTextPreference(context);
            endpoint.setKey("drop-zone:" + dropZoneId + ":endpoint");
            endpoint.setDialogTitle("API endpoint");
            endpoint.setDialogMessage("Usually starts with https://");
            endpoint.setTitle("API endpoint");
            endpoint.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            screen.addPreference(endpoint);

            EditTextPreference uploadToken = new EditTextPreference(context);
            uploadToken.setKey("drop-zone:" + dropZoneId + ":upload-token");
            uploadToken.setDialogTitle("API upload token");
            uploadToken.setDialogMessage("This is not your encryption key!");
            uploadToken.setTitle("Upload token");
            uploadToken.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
                }
            });
            uploadToken.setSummaryProvider(new Preference.SummaryProvider<EditTextPreference>() {
                @Override
                public CharSequence provideSummary(EditTextPreference preference) {
                    if (preference.getText() == null || preference.getText().isEmpty()) {
                        return "Not set";
                    } else {
                        return "Set";
                    }
                }
            });
            screen.addPreference(uploadToken);

            EditTextPreference publicKey = new EditTextPreference(context);
            publicKey.setKey("drop-zone:" + dropZoneId + ":public-key");
            publicKey.setDialogTitle("Public key");
            publicKey.setDialogMessage("For end-to-end encryption");
            publicKey.setTitle("Public key");
            publicKey.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            screen.addPreference(publicKey);

            setPreferenceScreen(screen);
        }
    }
}