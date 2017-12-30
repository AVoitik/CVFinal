package com.example.hittraxpc.hittraxmobile;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.support.annotation.MainThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.CV_CHAIN_APPROX_SIMPLE;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RETR_TREE;
import static org.bytedeco.javacpp.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.javacpp.opencv_imgproc.findContours;
import static org.bytedeco.javacpp.opencv_imgproc.minAreaRect;

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
import java.net.URI;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {

    private AndroidFrameConverter conv = new AndroidFrameConverter();
    private OpenCVFrameConverter.ToMat convToMatOne = new OpenCVFrameConverter.ToMat();
    private OpenCVFrameConverter.ToMat convToMatTwo = new OpenCVFrameConverter.ToMat();
    private TextView launchAngleText, exitVeloText, videoTitleText, timingText;
    private FFmpegFrameGrabber fg1, fg2;
    private int frameCount = 1;
    private Frame fr = new Frame();
    private Frame frameOne = new Frame();
    private Frame frameTwo = new Frame();
    private double launchAngleAvg = 0;
    private int launchAngleCounter = 0;
    private double exitVeloAvg = 0;
    private int exitVeloCounter = 0;
    private int oneOrTwo = 0;
    private int notFoundNum = 0;
    private Button btn;
    private double finalLA = 0;
    private double finalEV = 0;
    private int counter = 0;
    private long startTime;
    private long endTime;

    private boolean notFoundTrigger = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String TAG = "MainActivity";

        launchAngleText = (TextView)findViewById(R.id.launchAngleEditable);
        exitVeloText = (TextView)findViewById(R.id.exitVeloEditable);
        videoTitleText = (TextView)findViewById(R.id.videoTitle);
        timingText = (TextView)findViewById(R.id.timingEditable);

        btn = (Button)findViewById(R.id.button);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("video/mp4");
        startActivityForResult(intent, 0);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            }
        });



        //Here I am going to implement the video file selection button




    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            startTime = SystemClock.uptimeMillis();

            //Put name of video file here
            File file = new File(Environment.getExternalStorageDirectory() + "/Video/" + getFileName(uri));
//
            videoTitleText.setText(file.toString());

            //Init the two grabbers

            initializeGrabber(file);
            long newStart = SystemClock.uptimeMillis();

            algorithm(file);

            long newEnd = SystemClock.uptimeMillis();

            timingText.setText(Long.toString(newEnd - newStart));
        }
    }
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    //IN: a file pointing to the video to be processed
    //OUT: two globally initialized frame grabber objects
    private void initializeGrabber(File file){
        String TAG = "initializeGrabber";
        try{
            fg1.release();
            //fg2.release();
        }catch(Exception e){
            e.printStackTrace();
        }

        fg1 = new FFmpegFrameGrabber(file);
        //fg2 = new FFmpegFrameGrabber(file);
        try{
            fg1.setAudioChannels(0);
            fg1.setFormat("mov");
            fg1.setVideoCodec(avcodec.AV_CODEC_ID_MPEG2VIDEO);
            fg1.setVideoBitrate(1000000000);
            fg1.start();

            fg1.grabFrame();


        }catch(Exception ex){
            ex.printStackTrace();
            Log.d(TAG, "Init failed");

        }
    }

    private void algorithm(File file){

        Frame showFrame = new Frame();
        Bitmap bmp;
        String TAG = "Algorithm";

        opencv_core.Mat cropMatOne = new opencv_core.Mat();
        opencv_core.Mat cropMatTwo = new opencv_core.Mat();
        boolean hasFirstFrame;
        boolean hasSecondFrame;
        opencv_core.Rect myRect = new opencv_core.Rect(0, 0, 700, 720);
        opencv_core.Mat frameOneROI;
        opencv_core.Mat frameTwoROI;
        Mat rotatedFrameOne = new Mat();
        Mat rotatedFrameTwo = new Mat();
        Mat hierarchy = new Mat();
        int one = 0;
        int two = 1;



        //Main loop to grab images
        while(true){
            hasFirstFrame = false;
            hasSecondFrame = false;
            opencv_core.Mat grayOne = new opencv_core.Mat();
            opencv_core.Mat grayTwo = new opencv_core.Mat();
            Mat diffImg = new Mat();
            Mat threshImg = new Mat();
            MatVector contours = new MatVector();
            int dire;
            counter++;
            //Log.d(TAG, "ONE");
            if(read(1, fg1, TAG, file) == 0){
                //Log.d(TAG, "Found the end of the video");
                //Log.d(TAG, "frameOne broke");
                hasFirstFrame = false;
                break;
            }else{
                //Log.d(TAG, "Frame One#: " + counter);
                frameCount = frameCount + 1;
                hasFirstFrame = true;
                try{

                    cropMatOne = convToMatOne.convert(frameOne);
                    flip(cropMatOne, rotatedFrameOne, -1);
                    frameOneROI = new opencv_core.Mat(rotatedFrameOne, myRect);
                }catch(NullPointerException e){
                    e.printStackTrace();
                    Log.d(TAG, "frameOne broke in convert");
                    break;
                }

            }



            if(read(2, fg1, TAG, file) == 0){
                Log.d(TAG, "Found the end of the video");
                Log.d(TAG, "frame Two broke");
                hasSecondFrame = false;
                break;
            }else{
                //frameCount = frameCount + 1;
                //Log.d(TAG, "Frame two Count: " + frameCount);
                hasSecondFrame = true;
                try {

                    cropMatTwo = convToMatTwo.convert(frameTwo);
                    flip(cropMatTwo, rotatedFrameTwo, -1);
                    frameTwoROI = new opencv_core.Mat(rotatedFrameTwo, myRect);
                }catch(NullPointerException e){
                    e.printStackTrace();
                    Log.d(TAG, "frameTwo broke in convert");
                    break;
                }
            }

            if(hasFirstFrame && hasSecondFrame){
                //Convert to grayscale

                opencv_imgproc.cvtColor(frameOneROI, grayOne, opencv_imgproc.COLOR_BGR2GRAY);
                opencv_imgproc.cvtColor(frameTwoROI, grayTwo, opencv_imgproc.COLOR_BGR2GRAY);

                //Double differencing
                absdiff(grayOne, grayTwo, diffImg);

                //Threshold the difference
                opencv_imgproc.threshold(diffImg, threshImg, 50, 255, THRESH_BINARY);


                //run blob detection
                if(findBall(threshImg, frameCount)){

                    //Find contours in the found image

                    findContours(threshImg, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, new Point(0,0));





                    Vector<RotatedRect> minRect = new Vector<RotatedRect>();
                    for(int i = 0; i < contours.size(); i++) {
                        minRect.add(i, minAreaRect(contours.get(i)));


                        Point2f rect_points = new Point2f();

                        //minRect.elementAt(i).points(rect_points);
                        //showFrame = convToMatOne.convert(threshImg);
                        //bmp = conv.convert(showFrame);


                            //Log.d(TAG, "Coordinates: " + rect_points.x() + " y: " + rect_points.y());
                        //iv.setImageBitmap(bmp);



                        //break;



                    }

                    /* This following block uses the assumption that I have only 3 contours, or if there are more,
                    I assume that the first two that are picked up are the two baseballs to do the calculations.
                    This is a big assumption, but in my 9 tests as well as my personal bias towards it I feel this is
                    sufficient, but there can be room for improvement */

                    for(int k = 0; k < contours.size(); k++) {
                        if (contours.size() > 2) {
                            if (minRect.get(k).boundingRect2f().height() <  9) {
                                if (k > 1) {
                                    one = 0;
                                    two = 1;
                                    break;
                                } else if (k == 0) {
                                    one = 1;
                                    two = 2;
                                    break;
                                } else if (k == 1) {
                                    one = 0;
                                    two = 2;
                                    break;
                                } else {
                                    one = 0;
                                    two = 1;
                                }
                            } else {
                                one = 0;
                                two = 1;
                            }
                        }else{
                            one = 0;
                            two = 1;
                        }
                    }

                    //Grab the length from min. bounding rectangle. if there is a negative launch angle, this value will be NAN
                    double distance = Math.sqrt((double) (minRect.get(one).boundingRect2f().x() - minRect.get(two).boundingRect2f().x())
                                    + (double) (minRect.get(one).boundingRect2f().y() - minRect.get(two).boundingRect2f().y()));

                    // The compare number can literally be anything. If it is going down, the distance equation will return NAN
                    // which will obviously default to the else clause
                    if (distance > 5) {
                        dire = 0;
                    } else {
                        dire = 1;
                    }
                    finalEV = exitVeloAvg / exitVeloCounter;
                    finalLA = launchAngleAvg / launchAngleCounter;

                    double la = calculateLaunchAngle(dire, minRect.get(one).boundingRect2f().x(), minRect.get(two).boundingRect2f().x(),
                            minRect.get(one).boundingRect2f().y(), minRect.get(two).boundingRect2f().y() );

                    launchAngleAvg = launchAngleAvg + la;
                    launchAngleCounter++;


                    Log.d("AverageLA", "Average Launch Angle: " + (launchAngleAvg / launchAngleCounter));


                    double exVel = calculateExitVelocity(la, minRect.get(one).boundingRect2f().x(), minRect.get(two).boundingRect2f().x(),
                            minRect.get(one).boundingRect2f().y(), minRect.get(two).boundingRect2f().y() );

                    exitVeloAvg = exitVeloAvg + exVel;
                    exitVeloCounter++;
                    Log.d("ExitV", "Average Exit Velocity: " + (exitVeloAvg / exitVeloCounter) + " Count: " + exitVeloCounter);
                    if(launchAngleCounter == 1){
                        finalEV = exitVeloAvg / exitVeloCounter;
                        finalLA = launchAngleAvg / launchAngleCounter;
                    }
                    if(notFoundTrigger == false){
                        notFoundTrigger = true;
                        //Log.d("TRIGGER", "Not found trigger activated");
                    }
                }else if(notFoundTrigger){
                    notFoundNum++;
                    //Log.d("TRIGGER", "There has been " + notFoundNum + " frames sequentially with no baseballs in them");
                    if(notFoundNum > 6){
                        //L/og.d("TRIGGER", "We are now breaking");
                        break;
                    }

                }
            }


            grayOne.release();
            grayTwo.release();
            frameOneROI.release();
            frameTwoROI.release();
            diffImg.release();
            threshImg.release();
        }
        endTime = SystemClock.uptimeMillis();
        ///Log.d("TIMING", "Total Execution Time: " + (endTime - startTime));

        launchAngleText.setText(Double.toString(finalLA));
        exitVeloText.setText(Double.toString(finalEV));

    }
    private double calculateExitVelocity(double ev, float zeroX, float oneX, float zeroY, float oneY){
        float horizDist = zeroX - oneX;
        float vertDist = zeroY - oneY;
        double hypotenuse = Math.pow((double)horizDist, 2.0) + Math.pow((double)vertDist, 2.0);
        double result = Math.sqrt(hypotenuse);

        double pix_per_sec = result / .00833;
        double exitVelo = pix_per_sec / 141.273055;



        /* I dont understand these lines */
        if(ev < 0){
            if(ev >= -3){
                //Log.d("First", "*" + ev + "*");
                exitVelo = exitVelo + ((1 / Math.abs(ev) - 1.1) * 10);
            }else{
               // Log.d("AHHHHHHHH", "*" + ev + "*");
                exitVelo = exitVelo + ((1 / Math.abs(ev)) * 10) - 2;
            }

        }else if(ev < 10){
            //Log.d("Passed Value", "*" + ev + "*");
            exitVelo = exitVelo - 5;
        }else{
            //Log.d("YOUHITTHEELSE", "*" + ev + "*");
            double num = Math.abs(ev - 20);
            exitVeloAvg = exitVeloAvg + (((1 / num) * 10) + 5);
        }

        return exitVelo;
    }



    private double calculateLaunchAngle(int dir, float zeroX, float oneX, float zeroY, float oneY){

        float newOne = zeroX - oneX;
        float newTwo = zeroY - oneY;

        float mResult = newTwo / newOne;

        double launchAngle = Math.atan((double)mResult) * 180 / 3.1415;

        return launchAngle;
    }
    private boolean findBall(Mat thresh, int frameCount){
        String TAG = "findball";

        //Set up blob detect params
        opencv_features2d.SimpleBlobDetector.Params params = new opencv_features2d.SimpleBlobDetector.Params();
        params.minThreshold(30);
        params.maxThreshold(80);
        params.filterByColor(true);
        params.blobColor((byte)255);
        params.filterByArea(true);
        params.minArea(150f);
        params.filterByConvexity(false);
        params.filterByInertia(false);



        opencv_features2d.SimpleBlobDetector detector = opencv_features2d.SimpleBlobDetector.create(params);
        KeyPointVector keypoints = new KeyPointVector();
        Mat im_w_keypoints = new Mat();

        detector.detect(thresh, keypoints);

        if(keypoints.size() >= 2){

            return true;
        }else{
            return false;
        }

    }

    private int read(Integer which, FFmpegFrameGrabber grab, String TAG, File file){

        try{
            if(which == 1){
                frameOne = grab.grabImage();
                return 1;
            }else if(which == 2){
                frameTwo = grab.grabImage();
                return 1;
            }
            return 0;
        }catch(Exception ex){
            ex.printStackTrace();
            Log.d(TAG, "Frame grab failed");
            return 0;
        }
    }
}
