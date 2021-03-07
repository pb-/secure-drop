package dev.baecher.securedrop;

import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
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
            enqueueWork(uris);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            enqueueWork(uris);
        }
    }

    private void enqueueWork(ArrayList<Uri> uris) {
        String[] stringUris = new String[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            stringUris[i] = uris.get(i).toString();
        }

        Data.Builder data = new Data.Builder();
        data.putStringArray("uris", stringUris);
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