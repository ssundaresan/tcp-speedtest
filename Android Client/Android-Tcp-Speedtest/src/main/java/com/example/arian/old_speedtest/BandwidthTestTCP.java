/*authors: {srikanth,narseo}@icsi.berkeley.edu*/
package com.example.arian.old_speedtest;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

import android.util.Log;

public class BandwidthTestTCP {
    private String server;
    private int PORT;
    private int N_STREAMS;
    private String test;
    private int PACKET_SIZE;
    private int MAXDUR;
    private int reportGran;
    private CountDownLatch bwlatch;

    private int NUMARR = 10;
    private int TIMEOUT = 10000;
    private int randomized = 0;
    private String TAG = "NETALYZR_BWTCP";
    private final boolean debug = true;
    double[] aggrResults;
    double [] aggrBySecResults;
    double [][] bySecResults;

    //Create a stringbuffer to store the output
    private static StringBuffer outputBuffer = null;

    public BandwidthTestTCP (String server, //target server
                             String test,    //UPLINK or DOWNLINK
                             int nStreams,   //# of parallel TCP threads
                             int sPort,      //server port
                             int pSize,      //message size for test. Use small sizes ~100 for EDGE, etc and larger, ~10k, for wifi
                             int maxDur,     //duration of test
                             int reportGran, //granularity of test report, in ms
                             int randomized, //client can ask for a fully randomized stream if set to 1
                             CountDownLatch bwlatch){

        if (debug) Log.i(TAG,"\n\nstarting tcp bandwidth test to " + server + "\n\n");
        this.server = server;
        this.test = test;
        this.N_STREAMS = nStreams;
        this.PORT = sPort;
        this.MAXDUR = maxDur;
        this.PACKET_SIZE= pSize;
        this.reportGran = reportGran;
        this.bwlatch = bwlatch;
        this.aggrResults = new double[N_STREAMS];
        this.aggrBySecResults = new double[MAXDUR/reportGran];
        this.bySecResults = new double[N_STREAMS][MAXDUR/reportGran];
        this.randomized = randomized;
        if (debug) Log.i(TAG,"init done");
    }

    public String startThreads() {
        if (debug) Log.i(TAG,"\n\nin tcp bandwidth test thread\n\n");
        Socket[] socket = new Socket[N_STREAMS];
        CountDownLatch latch = new CountDownLatch(N_STREAMS);

        Arrays.fill(aggrResults, -1.0);

        try{

            ExecutorService executorService = Executors.newFixedThreadPool(N_STREAMS);
            List<Future<String>> futures= new ArrayList<Future<String>>();
            for(int i = 0; i < N_STREAMS; i++) {
                socket[i] = new Socket(this.server,PORT);
                futures.add(executorService.submit(new Client(socket[i],i,latch,test,MAXDUR,PACKET_SIZE,randomized)));
            }

            outputBuffer = new StringBuffer();
            try {
                Iterator<Future<String>> it = futures.iterator();
                try{
                    while(it.hasNext()){
                        Future<String> f = it.next();
                        outputBuffer.append(f.get());
                    }
                }catch(Exception e){
                    Log.e(TAG,"exception " + e.getMessage());
                    outputBuffer.append("");
                }
                executorService.shutdown();

            }
            catch (Exception e) {
                e.printStackTrace();
            }

        } catch(IOException e){
            if (debug) Log.i(TAG,"Could not connect to server" + e.getMessage(),e);
            outputBuffer.append("");
        }

        if (debug) Log.i(TAG,"Done threads.");

        bwlatch.countDown();
        return outputBuffer.toString();
    }

    private class Client implements Callable<String>{
        private Socket socket;
        private int clientid;
        private int MAXDUR;
        private int PACKET_SIZE;
        private CountDownLatch latch;
        private String test;
        private int randomized;

        public Client(Socket socket,
                      int clientid,
                      CountDownLatch latch,
                      String test,
                      int MAXDUR,
                      int PACKET_SIZE,
                      int randomized){
            this.socket = socket;
            this.clientid = clientid;
            this.latch = latch;
            this.test = test;
            this.MAXDUR = MAXDUR;
            this.PACKET_SIZE = PACKET_SIZE;
            this.randomized = randomized;
        }

        public String call(){
            StringBuffer returnData = new StringBuffer();
            try {
                socket.setSoTimeout(TIMEOUT);
            } catch (SocketException e1) {
                aggrResults[clientid] = -1.0;
                latch.countDown();
                return "";
            }
            BufferedReader In = null;
            DataOutputStream out = null;
            DataInputStream in = null;
            if (debug) Log.i(TAG, "In connection " + clientid);

            try {
                In = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                String testParam = "test:" + test + " duration:" + MAXDUR  + " pktsize:" + PACKET_SIZE + " reportgran:" + reportGran + " randomized:" + randomized + "\n";
                out.writeBytes(testParam);
                //testParamServer.split(" ");
            } catch (IOException e){
                aggrResults[clientid] = -1.0;
                latch.countDown();
                //TODO: Srikanth, does it return anything here???
                return "";
            }

            if (debug) Log.i(TAG, "client in " + test + " mode");
            if (test.equals("UPLINK")){
                returnData.append(sendData(In,out,clientid));
                if (debug) Log.i(TAG, test);
            }
            else if(test.equals("DOWNLINK")){
                returnData.append(recvData(in,out,clientid));
            }
            if (debug) Log.i(TAG, "Finished " + test + " " + clientid);

            try{
                socket.close();
            } catch (IOException e){
                if (debug) Log.i(TAG, "Couldn't close socket");
            }
            latch.countDown();
            return returnData.toString();
        }
    }

    private String sendData( BufferedReader summaryIn, DataOutputStream out,int clientid){
        try {
            Thread.sleep(500);
        } catch (InterruptedException e2) {
            aggrResults[clientid] = -1.0;
            return "";
        }

        //char fillerStr = '1';
        byte[][] messageArr = new byte[NUMARR][PACKET_SIZE];
        for (int i=0;i<NUMARR;i++){
            new Random().nextBytes(messageArr[i]);
            for (int j=0;j<PACKET_SIZE;j++){
                if (messageArr[i][j] == '+' || messageArr[i][j] == '~'){
                    messageArr[i][j] = '0';
                }
            }
        }
        long startTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();
        long totBytes = 0;
        try {
            out.writeBytes("~");
            out.flush();
        } catch (IOException e1){
            aggrResults[clientid] = -1.0;
            return "";
        }
        int cntMsg = 0;
        while (true){
            try {
                out.write(messageArr[cntMsg%NUMARR]);
                totBytes += messageArr[0].length;
                cntMsg += 1;
            } catch (SocketException e){
                aggrResults[clientid] = -1.0;
                if (debug) Log.i(TAG,"write data SE " + clientid, e);
                return "";
            }  catch (IOException e) {
                aggrResults[clientid] = -1.0;
                if (debug) Log.i(TAG,"write data IOE" + clientid, e);
                return "";
            } catch (Exception e){
                aggrResults[clientid] = -1.0;
                if (debug) Log.i(TAG,"general xception " + clientid, e);
                return "";

            }
            currentTime = System.currentTimeMillis();
            if (currentTime - startTime > MAXDUR){
                break;
            }
        }
        try {
            out.flush();
            out.writeBytes("+");
            out.flush();

        } catch (IOException e) {
            this.aggrResults[clientid] = -1.0;
            return "";
        }
        if (debug) Log.i(TAG, "Sent " + totBytes + " bytes");
        String summary = "";
        try{
            summary = summaryIn.readLine();
            if (debug) Log.i(TAG, summary);
        }
        catch(SocketTimeoutException e){
            if (debug) Log.i(TAG, "Couldn't read summary: STE");
            aggrResults[clientid] = -1.0;
            return "";
        } catch (IOException e) {
            if (debug) Log.i(TAG, "Couldn't read summary: IOE");
            aggrResults[clientid] = -1.0;
            return "";
        }
        return summary;
    }

    public String recvData(DataInputStream in, DataOutputStream out, int clientid){
        int bytesRead = 0;
        long startTime = -1;
        //long slowstartTime = -1;
        //long currentTime = (new Date()).getTime();
        long endTime = -1;
        byte[] messageArr = new byte[PACKET_SIZE];
        long totalBytes = 0;
        //long aggrBytes = 0;
        boolean start = false;
        boolean end = false;
        int currSec = 0;
        long prevTotal = 0;
        long prevCheck = 0;
        double aggrTput = -1.0;
        String summary = "";

        try{
            while (!end){

                bytesRead = in.read(messageArr);
                if(bytesRead > 0){
                    if (!start && messageArr[0] == '~'){
                        startTime = System.currentTimeMillis();
                        start = true;
                        prevCheck = startTime;
                    }
                    if (messageArr[bytesRead-1] == '+'){
                        endTime = System.currentTimeMillis();;
                        end = true;
                    }
                }
                if (bytesRead == -1){
                    if (System.currentTimeMillis() - startTime > TIMEOUT){
                        Log.i(TAG,"End of Stream");
                        break;
                    }
                }
                if (start){
                    totalBytes += bytesRead;
                    long ct = System.currentTimeMillis();
                    //Log.i(TAG,"" + (ct - startTime)+ " " +(ct - startTime)/reportGran+" " +((ct - startTime)/reportGran)*reportGran+" "+currSec);
                    if ((((ct - startTime)/reportGran)*reportGran) > currSec){
                        if  (currSec < MAXDUR){
                            bySecResults[clientid][currSec/reportGran] = (totalBytes - prevTotal)*8.0/(ct - prevCheck);
                            //if (debug) Log.i(TAG,"bysec " + clientid + " " + currSec/reportGran + " " + bySecResults[clientid][currSec/reportGran]);
                            prevTotal = totalBytes;
                            prevCheck = ct;
                            currSec += reportGran;
                        }
                    }
                }
            }
            if (debug) {
                Log.i(TAG, "End: Total received " + totalBytes + " in " + (endTime - startTime) + " msec" + " Throughput: "+( ((double)totalBytes)*8/(((double)(endTime)-(double)(startTime))) )/1000 +"Mbps");
            }
        } catch(SocketTimeoutException e){
            if (debug) Log.i(TAG, "Couldn't read data");
            aggrResults[clientid] = -1.0;
            return "";
        } catch (IOException e){
            if (debug) Log.i(TAG, "Couldn't read data");
            aggrResults[clientid] = -1.0;
            return "";
        } finally{
            if (start && end){
                DecimalFormat df = new DecimalFormat();
                df.setMaximumFractionDigits(2);
                df.setGroupingUsed(false);
                aggrTput = (double)(totalBytes)*8.0/(endTime - startTime);
                summary = "" + totalBytes + " " + (endTime - startTime)/1000.0 + " " + df.format(aggrTput) + " ";
                for (int i=0;i<bySecResults[clientid].length;i++){
                    summary = summary + i + ":" + df.format(bySecResults[clientid][i])  + ";";
                }
                try {
                    out.writeBytes(summary);
                } catch (IOException e) {
                    if (debug) Log.i(TAG, "Couldn't send summary to server.");
                }
                aggrResults[clientid] = aggrTput;
                if (debug) Log.i(TAG,summary);
                return summary;
            }
            else{
                if (debug) Log.i(TAG, "Invalid Test");
                aggrResults[clientid] = -1;
            }
        }
        return "";
    }
}