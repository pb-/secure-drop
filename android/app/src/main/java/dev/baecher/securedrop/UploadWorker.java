package dev.baecher.securedrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;

import dev.baecher.securedrop.data.Datom;
import dev.baecher.securedrop.data.Datoms;

public class UploadWorker extends Worker {
    private static String notificationChannelId = "securedrop";

    public UploadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        ArrayList<Uri> uris = new ArrayList<>();
        Data input = getInputData();
        for (String u : input.getStringArray("uris")) {
            uris.add(Uri.parse(u));
        }

        String endpoint = withTrailingSlash(input.getString("endpoint"));
        String uploadToken = input.getString("upload-token");
        String publicKey = input.getString("public-key");

        Log.i("worker", "endoint " + endpoint +
                "; upload token " + uploadToken.length() +
                " chars; public key " + publicKey.length() + " chars");

        try {
            setForegroundAsync(createForegroundInfo(uris.size(), 0, false));
            processMany(uris, endpoint, uploadToken, publicKey);
            setForegroundAsync(createForegroundInfo(uris.size(), uris.size(), true));
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    public void processMany(ArrayList<Uri> uris, String endpoint, String uploadToken, String publicKey) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException, ShortBufferException {
        Log.i("worker", "got " + uris.size() + " URI(s)");
        final int bufferSize = 1 << 20;
        final int tagSize = 16;
        final int ivSize = 12;
        final int chunkPayloadSize = bufferSize - tagSize - ivSize;

        byte[] buffer = new byte[bufferSize];
        Datoms datoms = new Datoms();
        datoms.add(new Datom(-1, "batch/size", Long.toString(uris.size())));
        datoms.add(new Datom(-1, "batch/uploaded-at", Long.toString(System.currentTimeMillis() / 1000)));

        URL url = new URL(endpoint + "blob");

        for (int uriIndex = 0; uriIndex < uris.size(); uriIndex++) {
            Uri uri = uris.get(uriIndex);
            Log.i("worker", "uri is " + uri.toString());
            String name = getFileName(uri);
            long size = getFileSize(uri);
            Log.i("worker", "file '" + name + "' (" + size + " bytes)");

            SecretKey encryptionKey = generateSymmetricKey();

            Cipher cipher = Cipher.getInstance("AES_256/GCM/NoPadding");

            byte[] encryptedKey = encryptKey(publicKey, encryptionKey.getEncoded());
            final long chunks = getChunks(chunkPayloadSize, size);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try {
                connection.setDoOutput(true);
                connection.setChunkedStreamingMode(0);
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setRequestProperty("Token", uploadToken);
                OutputStream out = connection.getOutputStream();

                out.write(encryptedKey);

                long totalBytes = 0;
                long chunk = 0;

                try (InputStream is = getApplicationContext().getContentResolver().openInputStream(uri)) {
                    while (true) {
                        int bytesRead = is.read(buffer, ivSize, chunkPayloadSize);
                        if (bytesRead == -1) {
                            break;
                        }
                        totalBytes += bytesRead;

                        byte[] iv = computeIv(chunks, chunk);
                        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(8 * tagSize, iv));

                        int bytesWritten = cipher.doFinal(buffer, ivSize, bytesRead, buffer, ivSize);
                        System.arraycopy(iv, 0, buffer, 0, ivSize);
                        out.write(buffer, 0, ivSize + bytesWritten);

                        chunk++;
                    }
                }

                out.flush();
                out.close();
                Log.i("worker", "read/wrote " + totalBytes + " bytes");

                String blobId = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                Log.i("worker", "blob id is " + blobId);

                int entity = -uriIndex - 2;
                datoms.add(new Datom(entity, "file/encrypted?", "true"));
                datoms.add(new Datom(entity, "file/ordinal", Integer.toString(uriIndex + 1)));
                datoms.add(new Datom(entity, "file/name", name));
                datoms.add(new Datom(entity, "file/size", Long.toString(size)));
                datoms.add(new Datom(entity, "file/blob-id", blobId));
                datoms.add(new Datom(entity, "file/batch-id", -1));

                setForegroundAsync(createForegroundInfo(uris.size(), 1 + uriIndex, false));
            } finally {
                connection.disconnect();
            }
        }

        postDatoms(endpoint + "datoms", uploadToken, datoms);
    }

    public static void postDatoms(String endpoint, String uploadToken, Datoms datoms) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Token", uploadToken);
            PrintWriter out = new PrintWriter(connection.getOutputStream());
            datoms.write(out);
            out.flush();
            out.close();

            Log.i("post-datoms", "response code " + connection.getResponseCode());

            if (connection.getResponseCode() >= 400) {
                Log.e("post-datoms", new BufferedReader(new InputStreamReader(connection.getErrorStream())).readLine());
            } else {
                InputStream in = connection.getInputStream();
                while (in.read() != -1) {
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    public static String withTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url;
        }

        return url + "/";
    }

    public static long getChunks(int chunkPayloadSize, long size) {
        return size / chunkPayloadSize + (size % chunkPayloadSize > 0 ? 1 : 0);
    }

    public String getFileName(Uri uri) {
        try (Cursor cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null)) {
            cursor.moveToFirst();
            return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
    }

    public long getFileSize(Uri uri) {
        try (Cursor cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null)) {
            cursor.moveToFirst();
            return cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
        }
    }

    public static SecretKey generateSymmetricKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public static byte[] computeIv(long chunks, long chunk) {
        byte[] iv = new byte[12];
        write48BitInt(chunks, iv, 0);
        write48BitInt(chunk, iv, 6);
        return iv;
    }

    public static void write48BitInt(long n, byte[] dest, int offset) {
        for (int i = 0; i < 6; i++) {
            dest[offset + i] = (byte) ((n >> (8 * (5 - i))) & 0xff);
        }
    }

    public static byte[] encryptKey(String publicKey, byte[] SymmetricKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKey)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pk);

        return cipher.doFinal(SymmetricKey);
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(int totalFiles, int completedFiles, boolean done) {
        Context context = getApplicationContext();
        Notification notification = new NotificationCompat.Builder(context, notificationChannelId)
                .setContentTitle("Secure Drop")
                .setContentText((done ? "Uploaded " : "Uploading ") + (totalFiles > 1 ? (totalFiles + " files") : "one file"))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setProgress(totalFiles, done ? 0 : completedFiles, false)
                .setOngoing(!done)
                .build();

        return new ForegroundInfo(0, notification);
    }

    public static NotificationChannel createNotificationChannel() {
        NotificationChannel nc = new NotificationChannel(
                notificationChannelId, "Secure Drop", NotificationManager.IMPORTANCE_LOW);
        nc.setDescription("Progress information on secure drops");
        return nc;
    }
}
