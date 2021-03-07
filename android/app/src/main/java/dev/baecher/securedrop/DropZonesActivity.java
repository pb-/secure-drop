package dev.baecher.securedrop;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class DropZonesActivity extends AppCompatActivity {

    public static class DropZone {
        final String id;
        final String name;

        public DropZone(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private DropZonesAdapter dropZonesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drop_zones);
        setTitle("Drop zones");

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        dropZonesAdapter = new DropZonesAdapter(getDropZones(this));
        RecyclerView v = findViewById(R.id.dropZonesList);
        v.setAdapter(dropZonesAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dropZonesAdapter.updateDropZones(getDropZones(this));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static ArrayList<DropZone> getDropZones(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> entries = prefs.getAll();
        ArrayList<DropZone> dropZones = new ArrayList<>();
        for (String key : entries.keySet()) {
            if (key.startsWith("drop-zone:") && key.endsWith(":name")) {
                dropZones.add(
                        new DropZone(stripSuffix(stripPrefix(key, "drop-zone:"), ":name"),
                                prefs.getString(key, "")));
            }
        }

        return dropZones;
    }

    public void onClickAdd(View v) {
        String dropZoneId = Integer.toString(Math.abs(new Random().nextInt()));
        PreferenceManager
                .getDefaultSharedPreferences(this)
                .edit()
                .putString("drop-zone:" + dropZoneId + ":name", "Untitled drop zone")
                .apply();

        Intent intent = new Intent(this, DropZoneSettingsActivity.class);
        intent.putExtra("dropZoneId", dropZoneId);
        startActivity(intent);
    }

    public static String stripPrefix(String s, String prefix) {
        if (!s.startsWith(prefix)) {
            return s;
        }

        return s.substring(prefix.length());
    }

    public static String stripSuffix(String s, String suffix) {
        if (!s.endsWith(suffix)) {
            return s;
        }

        return s.substring(0, s.length() - suffix.length());
    }
}