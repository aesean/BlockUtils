package com.aesean.blockutils;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void crash(View view) {
        throw new RuntimeException("Test for BlockUtils");
    }

    public void block(View view) {
        block(120);
        block(220);
        block(320);
        block(420);
        block(220);
    }

    private void block(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
