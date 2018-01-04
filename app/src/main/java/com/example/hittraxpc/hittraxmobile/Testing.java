package com.example.hittraxpc.hittraxmobile;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.THRESH_BINARY;


import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_features2d;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
/**
 * Created by Alex Voitik on 1/2/2018.
 */

public class Testing extends AppCompatActivity {

    private FFmpegFrameGrabber frameGrab;
    private Button btn;
    private static AndroidFrameConverter convToBmp = new AndroidFrameConverter();
    private static AndroidFrameConverter convToBmpTwo = new AndroidFrameConverter();
    private OpenCVFrameConverter.ToMat convToMat = new OpenCVFrameConverter.ToMat();
    private static OpenCVFrameConverter.ToMat convToMatSHOWONE = new OpenCVFrameConverter.ToMat();
    private static OpenCVFrameConverter.ToMat convToMatSHOWTWO = new OpenCVFrameConverter.ToMat();

    private ImageView iv, iv2;
    private  TextView launchAngleText, exitVeloText, videoTitleText, timingText , contourNum, exitVelola;
    private static Frame showFrameOne = new Frame();
    private static Frame showFrameTwo = new Frame();
    private int counter = 0;
    private static opencv_core.Rect myRect = new opencv_core.Rect(0, 0, 700, 720);
    private boolean hasFirst = false;
    private boolean hasSecond = false;
    private static opencv_core.Mat grayOne = new opencv_core.Mat();
    private static opencv_core.Mat grayTwo = new opencv_core.Mat();
    private static opencv_core.Mat copyOne = new opencv_core.Mat();
    private static opencv_core.Mat copyTwo = new opencv_core.Mat();
    private static Mat diffImg = new Mat();
    private static Mat threshImg = new Mat();
    private Bitmap showMeOne;
    private Bitmap showMeTwo;
    private Mat hierarchy = new Mat();
    private List<Point2f> exitVeloList = new ArrayList<Point2f>();
    private double  finalLaunchAngle = 0;
    private boolean notFoundTrigger = false;
    private int finalLaunchAngleCount = 0;
    private int notFoundNum = 0;
    private double timeExposure = 2.09;
    private long timeOne;
    private long timeTwo;
    private long diffTimeAvg = 0;
    private long diffTimeCounter = 0;
    private double diffTime;
    private double sausageSize = 0;
    private double sausageCounter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn = (Button) findViewById(R.id.button);
        launchAngleText = (TextView)findViewById(R.id.launchAngleEditable);
        exitVeloText = (TextView)findViewById(R.id.exitVeloEditable);
        videoTitleText = (TextView)findViewById(R.id.videoTitle);
        exitVelola = (TextView)findViewById(R.id.exitVelola);
        timingText = (TextView)findViewById(R.id.exposureTime);

        //contourNum = (TextView)findViewById(R.id.contourNumEditable);
    }

    @Override
    protected void onStart(){
        super.onStart();
        //iv2 = (ImageView) findViewById(R.id.iv2);

        String s = getIntent().getStringExtra("VIDEO_TITLE");
        //iv = (ImageView)findViewById(R.id.imageView);


        File file = new File(Environment.getExternalStorageDirectory() + "/Video/" + s);
        long startTime = SystemClock.uptimeMillis();
        initializeGrabber(file);
        //long secondStartTime = SystemClock.uptimeMillis();
        algorithm();
        long endTime = SystemClock.uptimeMillis();
        exitVeloText.setText(Long.toString(endTime - startTime));
        //contourNum.setText(Long.toString(endTime - secondStartTime));
        DecimalFormat df = new DecimalFormat("#.#");
        double magic_number = 1.748885184;

      if(sausageCounter <= 0) {
          exitVelola.setText("NO BALL DETECTED");
          timingText.setVisibility(View.INVISIBLE);
      }else{
            exitVelola.setText(df.format((sausageSize/sausageCounter) * magic_number));
            timingText.setText(Double.toString((sausageSize/sausageCounter)));
      }

        videoTitleText.setText(s);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        });

    }

    private boolean findBall(Mat thresh){
        String TAG = "findball";

        //Set up blob detect params
        opencv_features2d.SimpleBlobDetector.Params params = new opencv_features2d.SimpleBlobDetector.Params();
        params.minThreshold(30);
        params.maxThreshold(80);
        params.filterByColor(true);
        params.blobColor((byte)255);
        params.filterByArea(true);
        params.minArea(250f);

        params.filterByConvexity(false);
        params.filterByInertia(false);

        opencv_features2d.SimpleBlobDetector detector = opencv_features2d.SimpleBlobDetector.create(params);
        KeyPointVector keypoints = new KeyPointVector();
        Mat im_w_keypoints = new Mat();

        detector.detect(thresh, keypoints);

        if(keypoints.size() >= 2){
            tryCalculating(keypoints);
            return true;
        }else{
            return false;
        }
    }

    private void tryCalculating(KeyPointVector keys){

        String TAG = "tryCalculating";
        if(keys.size() == 2){
            float xSub = keys.get(1).pt().x() - keys.get(0).pt().x();
            float ySub = keys.get(1).pt().y() - keys.get(0).pt().y();
            float result = ySub / xSub;
            double launchAngle = Math.atan((double)result) * 180 / 3.1415;
            finalLaunchAngleCount++;
            finalLaunchAngle = finalLaunchAngle + launchAngle;

            launchAngleText.setText(Long.toString(Math.round((finalLaunchAngle / finalLaunchAngleCount))));

            //Log.d("CalculateLA", "Calculated");

            //Calculate Exit Velocity
            sausageSize = sausageSize + keys.get(0).size() + keys.get(1).size();
            sausageCounter = sausageCounter + 2;
            exitVeloList.add(new Point2f(keys.get(0).pt().x(),keys.get(0).pt().y()));
            exitVeloList.add(new Point2f(keys.get(1).pt().x(),keys.get(1).pt().y()));
            //Log.d(TAG, "Points: " + exitVeloList);
        }
    }

    private void initializeGrabber(File file){
        String TAG = "initializeGrabber";

        frameGrab = new FFmpegFrameGrabber(file);
        //fg2 = new FFmpegFrameGrabber(file);
        try{
            frameGrab.setAudioChannels(0);
            frameGrab.setFormat("mp4");
            frameGrab.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            frameGrab.start();

            //frameGrab.grabImage();

            Log.d(TAG, "Init successful");
        }catch(Exception ex){
            ex.printStackTrace();
            Log.d(TAG, "Init failed");

        }
    }


    private void algorithm(){
/*Uncomment to rotate */
        Mat rotatedFrameOne = new Mat();
        Mat rotatedFrameTwo = new Mat();
        OpenCVFrameConverter.ToMat convToMat = new OpenCVFrameConverter.ToMat();
        OpenCVFrameConverter.ToMat convToMatSHOWTHREE = new OpenCVFrameConverter.ToMat();
        while(true) {

            String TAG = "ButtonPress";

            //MatVector contours = new MatVector();

            boolean somethingHappened = false;

                try {
                    //Bitmap bmp;


                    Frame newFrame = frameGrab.grabImage();
                    counter++;
                    if(newFrame.image == null){
                        newFrame = frameGrab.grabImage();
                    }
                    timeOne = frameGrab.getTimestamp();
                    if(newFrame.image != null) {
                        Mat cropOne = convToMat.convert(newFrame);
                        //Log.d("TIMEONE", "" + timeOne);


                    /*Uncomment to rotate

                        flip(cropOne, cropOne, -1);
                        Mat rotatedFrameOneNew = new opencv_core.Mat(cropOne, myRect);
                        rotatedFrameOneNew.copyTo(copyOne);
                        cropOne.release();
                        rotatedFrameOneNew.release();*/
                    /*Uncomment to rotate */
                        Mat cropOneNew = new Mat(cropOne, myRect);
                        cropOneNew.copyTo(copyOne);

                        somethingHappened = false;
                        hasFirst = true;
                        //Log.d(TAG, "Successfully grabbed image in frame number: " + counter);
                    }else{
                        hasFirst = false;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    somethingHappened = true;
                    hasFirst = false;
                    //Log.d(TAG, "SOMETHING HAPPENED WITH FRAME ONE");
                }


                try {

                    //Bitmap bmpTwo;

                        Frame newFrameTwo = frameGrab.grabImage();
                    if(newFrameTwo.image == null){
                        newFrameTwo = frameGrab.grabImage();
                    }
                        counter++;
                        if (newFrameTwo.image != null) {


                            timeTwo = frameGrab.getTimestamp();
                            //Log.d("TIMETWO", "" + timeTwo);
                            Mat cropTwo = convToMatSHOWTHREE.convert(newFrameTwo);

                /*Uncomment to rotate

                            flip(cropTwo, cropTwo, -1);
                            Mat rotatedFrameTwoNew = new opencv_core.Mat(cropTwo, myRect);
                            rotatedFrameTwoNew.copyTo(copyTwo);
                            cropTwo.release();
                            rotatedFrameTwoNew.release();*/

                /*Uncomment to rotate */
                            Mat cropTwoNew = new Mat(cropTwo, myRect);
                            cropTwoNew.copyTo(copyTwo);

                            somethingHappened = false;
                            hasSecond = true;
                            //Log.d(TAG, "Successfully grabbed image in frame number: " + (counter + 1));
                        } else {
                            hasSecond = false;
                        }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    somethingHappened = true;
                    hasSecond = false;
                    //Log.d(TAG, "SOMETHING HAPPENED WITH FRAME TWO");

                }



            if (hasFirst && hasSecond) {

                //Log.d("HASBOTH", "true");
                opencv_imgproc.cvtColor(copyOne, grayOne, opencv_imgproc.COLOR_BGR2GRAY);
                opencv_imgproc.cvtColor(copyTwo, grayTwo, opencv_imgproc.COLOR_BGR2GRAY);

                absdiff(grayOne, grayTwo, diffImg);

                opencv_imgproc.threshold(diffImg, threshImg, 50, 255, THRESH_BINARY);

                if (findBall(threshImg)) {

                    diffTime = (timeTwo - timeOne) / 10000.00;
                    if((diffTime < 0) || (somethingHappened) || (diffTime > 3)){
                        diffTime = 696969;
                        Log.d("BAD", "BAD");
                    }
                    //Log.d("DIFFTIME", "" + diffTime);
                    //timingText.setText(Integer.toString(counter));
                    //showFrameOne = convToMatSHOWONE.convert(grayTwo);
                    //showMeTwo = convToBmp.convert(showFrameOne);
                    //iv.setImageBitmap(showMeTwo);

                    //showFrameTwo = convToMatSHOWTWO.convert(threshImg);
                    //showMeOne = convToBmpTwo.convert(showFrameTwo);
                    //iv2.setImageBitmap(showMeOne);

                    //findContours(threshImg, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, new Point(0, 0));

                    //Vector<RotatedRect> minRect = new Vector<RotatedRect>();
                    //for (int i = 0; i < contours.size(); i++) {
                    //    minRect.add(i, minAreaRect(contours.get(i)));
                    //}

                    if(!notFoundTrigger) {
                        videoTitleText.setText(Integer.toString(counter));
                        notFoundTrigger = true;
                    }
                }else if(notFoundTrigger){
                    notFoundNum++;
                    if(notFoundNum > 6){
                        //calcExitVelocity();
                        calcExposureTime();
                        break;
                    }
                }
            }else if(!(hasFirst || hasSecond)){
                break;
            }

            copyOne.release();
            copyTwo.release();
            grayOne.release();
            grayTwo.release();
            diffImg.release();
            rotatedFrameOne.release();
            rotatedFrameTwo.release();
            threshImg.release();


        }
    }

    private void calcExitVelocity(){

        double avgExitVelo = 0;
        double exitVeloCounter = 0;

        for(int i = 0; i < exitVeloList.size(); i++){
            if(((i + 1) < exitVeloList.size()) && (diffTime != 696969)){
                double value = Math.sqrt(Math.pow(exitVeloList.get(i).x() - exitVeloList.get(i+1).x(), 2.0) + Math.pow(exitVeloList.get(i).y() - exitVeloList.get(i+1).y(), 2.0));
                Log.d("SINGLEEXITVELO","DiffTime Used: " + (diffTime));
                //Log.d("SINGLEEXITVELO","DiffTime Used: " + value);
                double finalEV = value / 1.486714286;
                if(finalEV < 120){
                    Log.d("SINGLEEXITVELO", "Value: " + finalEV);
                    exitVeloCounter++;
                    avgExitVelo = avgExitVelo + finalEV;
                }

            }
        }
        DecimalFormat df = new DecimalFormat("#.#");

        exitVelola.setText(df.format((avgExitVelo / exitVeloCounter)));
        //Log.d("EXITVELO", "Avg: " + (avgExitVelo / exitVeloCounter));
    }

    private void calcExposureTime(){
        double avgExposure = 0;
        double exposureCounter = 0;

        for(int i = 0; i < exitVeloList.size(); i++){
            if((i + 1) < exitVeloList.size()){
                double value = Math.sqrt(Math.pow(exitVeloList.get(i).x() - exitVeloList.get(i+1).x(), 2.0) + Math.pow(exitVeloList.get(i).y() - exitVeloList.get(i+1).y(), 2.0));
                //Distance
                //Need to find exposure Time....test videos!!!

                double speed = 83.1;
                double finalEX = value / speed;
                Log.d("EXPSINGLE", "" + finalEX);
                exposureCounter++;
                avgExposure = avgExposure + finalEX;
            }
        }
        Log.d("EXPOSURETIME", "Avg: " + (avgExposure / exposureCounter));
        timingText.setText(Double.toString(avgExposure / exposureCounter));
    }
}
