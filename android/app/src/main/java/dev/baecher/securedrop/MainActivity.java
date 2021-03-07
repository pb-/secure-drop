package dev.baecher.securedrop;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerNotificationChannel();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(uri);
            selectDropZone(uris);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            selectDropZone(uris);
        }
    }

    private void selectDropZone(final ArrayList<Uri> uris) {
        final ArrayList<DropZonesActivity.DropZone> dropZones = DropZonesActivity.getDropZones(this);

        if (dropZones.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("There are no drop zones configured, please add at least one")
                    .setTitle("No drop zone")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {}
                    });
            builder.show();
        } else if (dropZones.size() == 1) {
            enqueueWork(uris, dropZones.get(0).id);
        } else {
            String[] dropZoneLabels = new String[dropZones.size()];
            for (int i = 0; i < dropZones.size(); i++) {
                dropZoneLabels[i] = dropZones.get(i).name;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pick drop zone");
            builder.setItems(dropZoneLabels, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enqueueWork(uris, dropZones.get(which).id);
                }
            });
            builder.show();
        }
    }

    private void enqueueWork(ArrayList<Uri> uris, String dropZoneId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String[] stringUris = new String[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            stringUris[i] = uris.get(i).toString();
        }

        Data.Builder data = new Data.Builder();
        data.putStringArray("uris", stringUris);
        data.putString("endpoint", prefs.getString("drop-zone:" + dropZoneId + ":endpoint", ""));
        data.putString("upload-token", prefs.getString("drop-zone:" + dropZoneId + ":upload-token", ""));
        data.putString("public-key", prefs.getString("drop-zone:" + dropZoneId + ":public-key", ""));
        WorkManager
                .getInstance(getApplicationContext())
                .enqueue(new OneTimeWorkRequest.Builder(UploadWorker.class).setInputData(data.build()).build());
    }

    private void registerNotificationChannel() {
        getSystemService(NotificationManager.class).createNotificationChannel(
                UploadWorker.createNotificationChannel());
    }

    public void onClickDropZones(View view) {
        startActivity(new Intent(this, DropZonesActivity.class));
    }
}