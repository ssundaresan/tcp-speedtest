package com.example.arian.old_speedtest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;

import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



    }
    public void clickbutton(View view) {
        Log.i("wow", "client clickeed");
        final EditText stream = (EditText)findViewById(R.id.streams);

        final EditText packetsize  = (EditText)findViewById(R.id.packetsize);
        final EditText testduration  = (EditText)findViewById(R.id.testduration);

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
                        true,
                        new CountDownLatch(5));
                bandwidthTestTCP.startThreads();

//                 public BandwidthTestTCP (String server, //target server
//                        String test,    //UPLINK or DOWNLINK
//                int nStreams,   //# of parallel TCP threads
//                int sPort,      //server port
//                int pSize,      //message size for test. Use small sizes ~100 for EDGE, etc and larger, ~10k, for wifi
//                int maxDur,     //duration of test
//                int reportGran, //granularity of test report, in ms
//                CountDownLatch bwlatch){

                }
        });







            t.start();
    }

}
