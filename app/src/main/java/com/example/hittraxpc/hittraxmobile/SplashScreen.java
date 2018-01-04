package com.example.hittraxpc.hittraxmobile;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.widget.Spinner;

import java.io.File;

/**
 * Created by HitTraxPC on 12/11/2017.
 */

public class SplashScreen extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String s = getIntent().getStringExtra("VIDEO_TITLE");
        Intent intent = new Intent(SplashScreen.this, Testing.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("VIDEO_TITLE", s);
        overridePendingTransition(0, 0);
        startActivity(intent);

        finish();
    }
    


}