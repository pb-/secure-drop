package dev.baecher.securedrop;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

public class MainActivity extends AppCompatActivity {
    String publicKeyString = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAm+WJqPxT2zfDqBTpomw7GSoJhC9SaL0d+SxXC6YsTJqkQ9uxqN3ronwcCmp0lFlQIs5DinJBYQ9yyAzr5ytavEV/jr1a1w6BzPjWDOycAEgla8GYiCEXOqyKB9cglxiaaKl4C7dWPsz05anxKgiaQgNJyFuzCOchKEnM/g2gRPHIYVKbK8t52kKgPFcxSGevCERqEbhjEhkiYt7i7rWTucmipUwfHs6bB2NfyrE4e7SHoLGf1xtuDO7wkVcRJU+oZAmF+vbJxqhx7h4JT3a7lvd5lq/IWWQoXlFNaTpR0ekxKNZqab1dCD8ZWGtux9h7VoCOSDliQcTOIyV0ZxPevQ4ZcX5Y3HTZbMuzsXisF18tDyeG8aADO0pUmf/hBmvJlXfe/bdmB/nO5HaO9oYsuy4/jNlYU8650QKbtyVoI9VTj2DMFoFBKyS2S908IY03/J9jgchQsaCby8mMCbJYxgIZYct6az3QA1IXvX5REhraFEYDKxFtW1Kt7osqTFKERA7Tl+FEpRXsZJzcYSloPzuh/cLIYJ33u3z+BAHk/bEmNMTeHgFzMi80R4k4mOpLAoc4XEh/3TkfpYOBthPg+KBAObHObf8WGJ7ARmnEqWKDdHHxaMFQwFMyyeymJugPshU/jY3MK2Kg0I266qW6VrYbyn+bOAlXc6vNEeSiwMsCAwEAAQ==";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Intent.ACTION_SEND.equals(action) && type != null) {
                        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        if (uri != null) {
                            Log.i("main", "got single");
                            ArrayList<Uri> uris = new ArrayList<>();
                            uris.add(uri);
                            processMany(uris);
                        }
                    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);

                        if (uris != null) {
                            Log.d("main", "got many");
                            processMany(uris);
                        }
                    }
                } catch (Exception e) {
                    Log.e("main", e.toString());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void processMany(ArrayList<Uri> uris) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException, ShortBufferException {
        Log.i("process", "got " + uris.size() + " URI(s)");
        final int bufferSize = 1 << 20;
        final int tagBytes = 16;
        final int ivBytes = 12;
        final int chunkPayloadSize = bufferSize - tagBytes - ivBytes;

        byte[] buffer = new byte[bufferSize];

        URL url = new URL("http://10.0.0.20:4711/api/blob");

        for (Uri uri : uris) {
            Log.i("process", "uri is " + uri.toString());
            String name = getFileName(uri);
            long size = getFileSize(uri);
            Log.i("process", "file '" + name + "' (" + size + " bytes)");

            SecretKey encryptionKey = generateSymmetricKey();

            Cipher cipher = Cipher.getInstance("AES_256/GCM/NoPadding");

            byte[] encryptedKey = encryptKey(publicKeyString, encryptionKey.getEncoded());
            final long chunks = size / chunkPayloadSize + size % chunkPayloadSize > 0 ? 1 : 0;

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try {
                connection.setDoOutput(true);
                connection.setChunkedStreamingMode(0);
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                OutputStream out = connection.getOutputStream();

                out.write(encryptedKey);

                long totalBytes = 0;
                long chunk = 0;

                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    while (true) {
                        int bytesRead = is.read(buffer, ivBytes, chunkPayloadSize);
                        if (bytesRead == -1) {
                            break;
                        }
                        totalBytes += bytesRead;

                        byte[] iv = computeIv(chunks, chunk);
                        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(8 * tagBytes, iv));

                        int bytesWritten = cipher.doFinal(buffer, ivBytes, bytesRead, buffer, ivBytes);
                        System.arraycopy(iv, 0, buffer, 0, ivBytes);
                        out.write(buffer, 0, ivBytes + bytesWritten);

                        chunk++;
                    }
                }

                out.flush();
                out.close();
                Log.i("process", "read/wrote " + totalBytes + " bytes");

                String blobId = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                Log.i("process" ,"blob id is " + blobId);
            } finally {
                connection.disconnect();
            }
        }
    }

    public String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            cursor.moveToFirst();
            return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
    }

    public long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
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
}