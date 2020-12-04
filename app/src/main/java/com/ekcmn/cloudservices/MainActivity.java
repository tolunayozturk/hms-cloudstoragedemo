package com.ekcmn.cloudservices;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.huawei.agconnect.auth.AGConnectAuth;
import com.huawei.agconnect.auth.AGConnectUser;
import com.huawei.agconnect.cloud.storage.core.AGCStorageManagement;
import com.huawei.agconnect.cloud.storage.core.StorageReference;
import com.huawei.agconnect.cloud.storage.core.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();

    AGCStorageManagement storageManagement;
    AGConnectUser user;

    ImageView iv_profile_photo;
    Button btn_upload;
    TextView textView;
    ProgressBar progressBar;
    Button btn_delete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iv_profile_photo = findViewById(R.id.iv_profile_photo);
        btn_upload = findViewById(R.id.btn_upload);
        textView = findViewById(R.id.textView);
        progressBar = findViewById(R.id.progressBar);
        btn_delete = findViewById(R.id.btn_delete);

        storageManagement = AGCStorageManagement.getInstance();

        AGConnectAuth.getInstance().signInAnonymously().addOnSuccessListener(signInResult -> {
            Log.i(TAG, "signInAnonymously() success");
            user = signInResult.getUser();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "signInAnonymously() failure", e);
        });

        // Request read and write permissions at runtime
        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
        };
        ActivityCompat.requestPermissions(this, permissions, 1);

        btn_upload.setOnClickListener(view -> {
            launchPhotoLibrary();
        });

        btn_delete.setOnClickListener(view -> {
            deleteProfilePicture();
        });
    }

    private void upload(Uri uri) {
        if (user == null) {
            Log.w(TAG, "upload: sign in first");
            return;
        }

        StorageReference ref = storageManagement.getStorageReference(
                user.getUid() + "/" + "pp" + ".jpg");
        byte[] bytes = null;
        try {
            bytes = readBytes(uri);
        } catch (IOException e) {
            Log.e(TAG, "upload: " + e.getMessage(), e);
        }

        if (bytes == null) {
            Log.w(TAG, "upload: couldn't read bytes from uri");
            return;
        }

        progressBar.setProgress(0);
        UploadTask uploadTask = ref.putBytes(bytes, null);
        uploadTask.addOnSuccessListener(uploadResult -> {
            Log.i(TAG, "upload: success");
            uploadResult.getMetadata().getStorageReference().getDownloadUrl()
                    .addOnSuccessListener(url -> {
                        Log.d(TAG, "getDownloadUrl: " + url.toString());
                        textView.setText(url.toString());
                        Picasso.get().load(url).into(iv_profile_photo);
                    }).addOnFailureListener(e -> {
                Log.e(TAG, "getDownloadUrl: " + e.getMessage(), e);
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "upload: " + e.getMessage(), e);
        }).addOnProgressListener(uploadResult -> {
            progressBar.setProgress((int) (uploadResult.getBytesTransferred() * 100 /
                    uploadResult.getTotalByteCount()));
        });
    }

    public void deleteProfilePicture() {
        if (user == null) {
            Log.w(TAG, "upload: sign in first");
            return;
        }

        StorageReference ref = storageManagement.getStorageReference(
                user.getUid() + "/" + "pp" + ".jpg");

        ref.delete().addOnSuccessListener(aVoid -> {
            Log.i(TAG, "deleteProfilePicture: success");
        }).addOnFailureListener(e -> {
            Log.e(TAG, "deleteProfilePicture: " + e.getMessage(), e);
        });
    }

    private void launchPhotoLibrary() {
        Intent choosePhotoIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .setType("image/*");

        if (choosePhotoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(
                    choosePhotoIntent, 200
            );
        }
    }

    private byte[] readBytes(Uri uri) throws IOException {
        InputStream stream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        int i;
        while ((i = stream.read(buffer, 0, buffer.length)) > 0) {
            byteArrayStream.write(buffer, 0, i);
        }

        return byteArrayStream.toByteArray();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 200:
                if (resultCode == Activity.RESULT_OK) {
                    Uri selectedImageUri = data.getData();
                    Log.d(TAG, "onActivityResult: " + selectedImageUri.getPath());
                    upload(selectedImageUri);
                }
                break;
        }
    }
}