package com.example.arian.old_speedtest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;

import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void clickbutton(View view) {
        final EditText stream = (EditText)findViewById(R.id.streams);
        final EditText packetsize  = (EditText)findViewById(R.id.packetsize);
        final EditText testduration  = (EditText)findViewById(R.id.testduration);
        final CheckBox randomized_checkbox = (CheckBox)findViewById(R.id.checkBox);
        final int randomized;

        if(randomized_checkbox.isChecked()){
            randomized = 1;
        }
        else{
            randomized = 0;
        }
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                CountDownLatch bwlatch ;
                BandwidthTestTCP bandwidthTestTCP = new BandwidthTestTCP("10.0.1.5","DOWNLINK",Integer.parseInt(stream.getText().toString()),
                        9999,
                        Integer.parseInt(packetsize.getText().toString()),
                        Integer.parseInt(testduration.getText().toString()),
                        1,
                        randomized, //randomized
                        new CountDownLatch(5));
                bandwidthTestTCP.startThreads();
                }
        });
            t.start();
    }

}
