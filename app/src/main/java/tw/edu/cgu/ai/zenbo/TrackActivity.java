/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*  This file has been modified by Nataniel Ruiz affiliated with Wall Lab
 *  at the Georgia Institute of Technology School of Interactive Computing
 */

package tw.edu.cgu.ai.zenbo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotFace;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;


public class TrackActivity extends Activity {
    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;

    public RobotAPI mRobotAPI;
//    RobotCallback robotCallback;
//    DialogCallback dsCallback;

    public final static String DOMAIN = "5823CA7CDACF43EEAF4417D783FB1321";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Chih-Yuan Yang: setContentView is a function of the Activity class. It can change the page layout
        //but keeps variables unchanged.
        //This is the most necessary task in the onCreate() callback function.
        //I am confused here because my layout/activity_camera.xml is almost empty. Does this statement matter?
        //The "activity_camera" is the src/main/res/layout/activity_camera.xml, but the content is almost empty.
        setContentView(R.layout.activity_camera);

        if (hasPermission()) {
            if (null == savedInstanceState) {
                setFragment();
            }
        } else {
            requestPermission();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

//        this.mRobotAPI = new RobotAPI(getApplicationContext(), robotCallback);
        this.mRobotAPI = new RobotAPI(getApplicationContext());

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    setFragment();
                } else {
                    requestPermission();
                }
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
//        if( dsCallback == null)
//            dsCallback = new DialogCallback();

//        if (dsCallback != null)
//            mRobotAPI.robot.registerListenCallback(dsCallback);
        mRobotAPI.robot.setExpression(RobotFace.HIDEFACE);
        mRobotAPI.robot.setPressOnHeadAction(false);
        mRobotAPI.robot.setVoiceTrigger(false);     //disable the voice trigger
//        mRobotAPI.robot.jumpToPlan(DOMAIN, "ShowFace");

        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;       //Why do I still see the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);

        //9/13/2018 Chih-Yuan: create a thread to establish a socket connection
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRobotAPI.robot.unregisterListenCallback();
        mRobotAPI.robot.setExpression(RobotFace.DEFAULT);
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    shouldShowRequestPermissionRationale(PERMISSION_STORAGE) ||
                    shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO)) {
                Toast.makeText(this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO}, PERMISSIONS_REQUEST);
        }
    }

    private void setFragment() {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, CameraConnectionFragment.newInstance())
                .commit();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }
}
