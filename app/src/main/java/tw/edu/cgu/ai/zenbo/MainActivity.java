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

import static java.lang.Thread.sleep;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotFace;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Button;

import tw.edu.cgu.ai.zenbo.env.Logger;
import ImageAnalyzedResults.AnalyzedResults;
import ImageAnalyzedResults.AnalyzedResults.ReportData;


public class MainActivity extends Activity {
    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;

    public RobotAPI mRobotAPI;
    private ZenboCallback robotCallback;
    private static final Logger LOGGER = new Logger();

    private KeyPointView keypointView;
    private InputView inputView;     //2024/6/25 Chih-Yuan Yang: The purpose of the inputView is to get a frame from camera's preview.
    //Thus, I can send the frame to a server.
    private ActionRunnable mActionRunnable = new ActionRunnable();
    private CheckBox checkBox_keep_alert;
    private CheckBox checkBox_enable_connection;
    private CheckBox checkBox_show_face;
    private CheckBox checkBox_dont_move;
    private CheckBox checkBox_dont_rotate;
    private Button button_close;
    private MessageView mMessageView_Detection;
    private MessageView mMessageView_Timestamp;
    private EditText editText_Server;
    private EditText editText_Port;
    private DataBuffer m_DataBuffer;
    private MediaRecorder mMediaRecorder;
    private String mVideoAbsolutePath;
    private Size mPreviewSize = new Size(640, 480);
    private CameraDevice mCameraDevice;
    private HandlerThread threadImageListener;
    private Handler handlerImageListener;
    private HandlerThread threadSendToServer;
    private Handler handlerSendToServer;
    private HandlerThread mThreadAction;
    private Handler mHandlerAction;
    private HandlerThread mThreadSendAudio;
    private Handler mHandlerSendAudio;
    private ImageReader mPreviewReader;     //used to get onImageAvailable, not used in Camera2Video project
    private CaptureRequest.Builder mPreviewBuilder;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private boolean mIsRecordingVideo = false;

    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mPreviewSession;
    /*Chih-Yuan Yang 2024/6/16: ImageListener is defined in the ImageListener.java, which is derived
     from ImageReader.OnImageAvailableListener, an interface.
     */
    private final ImageListener mPreviewListener = new ImageListener();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    Socket mSocketReceiveResults;
    Socket mSocketSendImages;
    Socket mSocketSendAudio;
    private String mServerURL;
    private Integer mPortNumber;

    java.util.Timer timer_get_analyzed_results;
    InputStreamReader mInputStreamReader;
    BufferedReader mBufferedReader_inFromServer;

    TimerTask task_get_analyzed_results = new TimerTask() {
        public void run() {
            //2024/7/15 Why does this TimerTask stop when sockets are connected? As a result, I cannot receive messages sent back from the server.
//            Log.d("TimerTask", "into task_get_analyzed_results");
            final long timestamp_start = System.currentTimeMillis();
            char[] buffer = new char[2048];
//            String result = "";
//            String line;
            if (mSocketReceiveResults != null && mSocketReceiveResults.isConnected()) {
//                Log.d("TimerTask", "Before read");
                try {
//                    DataInputStream inputStream = new DataInputStream(mSocketReceiveResults.getInputStream());
//                    byte[] buffer = new byte[2048];
                    mInputStreamReader = new InputStreamReader(mSocketReceiveResults.getInputStream());
                    mBufferedReader_inFromServer = new BufferedReader(mInputStreamReader);
                    if( mBufferedReader_inFromServer.ready()) {
                        //The buffer contains multiple proto buffer messages
                        int len = mBufferedReader_inFromServer.read(buffer, 0, 2048);
                        if (len != -1) {
                            String string = new String(buffer,0,len);
//                            String beginString = new String("BeginOfAMessage");
//                            String endString = new String("EndOfAMessage");
//                            while(true) {
//                                int iBegin = string.indexOf(beginString);
//                                int iEnd = string.indexOf(endString);
//                                if (iBegin != -1 && iEnd != -1) {
//                                    String messageString = string.substring(iBegin + beginString.length()+1, iEnd);  //there is an \n at the end
//                                    string = string.substring(iEnd + endString.length()+1);
//                                    ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(messageString);
                                    ReportData report = ReportData.parseFrom (string.getBytes());
//                    ReportData report = ReportData.parseFrom (inputStream);
//                            Log.d("report", report.getKey());
                            if (report.hasSpeakSentence())
                                Log.d("Speak Sentence", report.getSpeakSentence());
                        }
//                                else
//                                    break;
//                            }
//                        }
                   }
                }
                catch (Exception e)
                {
                    Log.e("Exception", e.getMessage());
                }

            }
//            try {
//                if( mBufferedReader_inFromServer != null) {
//                    while (mBufferedReader_inFromServer.ready() && (line = mBufferedReader_inFromServer.readLine()) != null )
//                    {
//                        Log.d("TimerTask", "BeforeReadline");
                        //if the server is down, the line will be null, and there will be an exception.
//                        result += (line + "\n");
//                        Log.d("TimerTask", "EndReadline");
//                        if( line.equals("EndToken"))
//                            break;
//                        ReportData report = ReportData.parseFrom(line.getBytes());
//                        Log.d("Server Return", report.getSpeakSentence());
//                    }
//                    m_DataBuffer.AddNewFrame(result);
//                    mHandlerAction.post(mActionRunnable);
//                    keypointView.setResults(m_DataBuffer.getLatestFrame());
//                }


//            } catch (Exception e) {
//                Log.d("Exception", e.getMessage());
//            }
//            final long timestamp_end = System.currentTimeMillis();
        }
    };

    //2024/6/25 Chih-Yuan Yang: Why do I need the surfaceTextureListener?
    /**
     * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };


    /**
     * {@link android.hardware.camera2.CameraDevice.StateCallback}
     * is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cameraDevice) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    mCameraDevice = cameraDevice;
                    boolean bRecordVideo = false;
                    if(bRecordVideo)
                        //Chih-Yuan Yang 2024/6/16: This is the reason I never record videos. I set the boolean variable fixed.
                        startRecordingVideo();      //The startRecordingVideo is called in a callback function. Is it fine?
                    else
                        startPreview();
                    cameraOpenCloseLock.release();  //Chih-Yuan Yang 2024/6/16: The cameraOpenCloseLock is a semaphore.
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    mCameraDevice = null;
                }
            };

    private void showToast(final String text) {
        MainActivity.this.runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

//    AudioRecord recorder;

    private int sampleRate = 44100 ; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);   //minBufSize = 5376, but larger is better.
    private boolean status = true;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
//        setContentView(R.layout.main_activity_redminote8t);

        if (!hasPermission()) {
            requestPermission();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        robotCallback = new ZenboCallback();
        mRobotAPI = new RobotAPI(this, robotCallback);
        mActionRunnable.ZenboAPI = mRobotAPI;
        mActionRunnable.robotCallback = robotCallback;

        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);
        inputView = (InputView) findViewById(R.id.inputview);
        keypointView = (KeyPointView) findViewById(R.id.keypoint);
        editText_Port = (EditText) findViewById(R.id.editText_Port);
        editText_Server = (EditText) findViewById(R.id.editText_Server);
        checkBox_keep_alert = (CheckBox) findViewById(R.id.checkBox_keepalert);
        checkBox_enable_connection = (CheckBox) findViewById(R.id.checkBox_connect);
        checkBox_show_face = (CheckBox) findViewById(R.id.checkBox_ShowFace);
        checkBox_dont_move = (CheckBox) findViewById(R.id.checkBox_DontMove);
        checkBox_dont_rotate = (CheckBox) findViewById(R.id.checkBox_DontRotate);
        mMessageView_Detection = (MessageView) findViewById(R.id.MessageView_Detection);
        mMessageView_Timestamp = (MessageView) findViewById(R.id.MessageView_Timestamp);
        button_close = (Button) findViewById(R.id.button_close);

        mServerURL = editText_Server.getText().toString();;
        editText_Server.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {   // 按下完成按钮，这里和上面imeOptions对应
                    mServerURL = v.getText().toString();;
                }
                return false;//返回true，保留软键盘。false，隐藏软键盘
            }
        });


        mPortNumber = Integer.parseInt(editText_Port.getText().toString());
        editText_Port.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {   // 按下完成按钮，这里和上面imeOptions对应
                    mPortNumber = Integer.parseInt(v.getText().toString());
                }
                return false;//返回true，保留软键盘。false，隐藏软键盘
            }
        });

        checkBox_enable_connection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HandlerThread thread = new HandlerThread("SocketProcess");
                thread.start();
                Handler handler = new Handler(thread.getLooper());
                if (isChecked) {
//                    recorder.startRecording();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mSocketSendImages = new Socket(mServerURL, mPortNumber);
                                if( mSocketSendImages.isConnected()) {
                                    mPreviewListener.set_socket(mSocketSendImages);
                                }
                                mSocketReceiveResults = new Socket(mServerURL, mPortNumber + 1);
//                                mSocketSendAudio = new Socket(mServerURL, mPortNumber + 2);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
/*
                            if( mSocketSendAudio != null) {
                                //I need to move this piece of code somewhere else

                                status = true;
                                mHandlerSendAudio.post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                short[] buffer = new short[minBufSize];   //minBufSize = 5376
                                                Log.d("VS", "Buffer created of size " + minBufSize);           // every 5 second, the log message occurs. It does not make sense.

                                                OutputStream os = mSocketSendAudio.getOutputStream();
                                                int readSize;
                                                while (status) {
                                                    //reading data from MIC into buffer
                                                    readSize = recorder.read(buffer, 0, buffer.length);
                                                    if( readSize >= 0) {
                                                        byte[] byteBuffer = ShortToByte_Twiddle_Method(buffer);
                                                        os.write(byteBuffer, 0, readSize * 2);
                                                    }
                                                    else
                                                    {
                                                        Log.e("recorder","recorder.read() error");
                                                    }
                                                }
                                            } catch (UnknownHostException e) {
                                                Log.e("VS", "UnknownHostException");
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                Log.e("VS", "IOException");
                                            }
                                        }
                                    }
                                );
                            }
*/
                        }
                    });
                }
                else {
//                    recorder.stop();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            disconnectSockets();
                        }
                    });
                }
            }
        });

        checkBox_show_face.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mActionRunnable.mShowRobotFace = isChecked;
            }
        });

        checkBox_dont_move.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mActionRunnable.bDontMove = isChecked;
            }
        });

        checkBox_dont_rotate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mActionRunnable.bDontRotateBody = isChecked;
            }
        });

        button_close.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                mActionRunnable.ZenboAPI.release();
                                                finish();
                                                //moveTaskToBack(true);
                                            }
                                        }
        );


        checkBox_keep_alert.setOnCheckedChangeListener( new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                mActionRunnable.bKeepAlert = isChecked;
            }

        });

        mActionRunnable.setMessageView(mMessageView_Detection, mMessageView_Timestamp);
        m_DataBuffer = new DataBuffer(100);
        mActionRunnable.setDataBuffer(m_DataBuffer);

        timer_get_analyzed_results = new java.util.Timer(true);
        timer_get_analyzed_results.schedule(task_get_analyzed_results, 1000, 33);

        mRobotAPI.robot.speak("哈囉，你好。");
    }  //end of onCreate

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    requestPermission();
                }
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        startThreads();

        mRobotAPI.robot.setExpression(RobotFace.HIDEFACE);
        mRobotAPI.robot.setPressOnHeadAction(false);
        mRobotAPI.robot.setVoiceTrigger(false);     //disable the voice trigger

        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;       //Why do I still see the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);

//        if(recorder == null) {
//            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize * 10);   //5376* 10
//            Log.d("VS", "Recorder initialized");
//        }

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera();   //Chih-Yuan Yang 2024/6/25: This openCamera never be called
        } else {
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        try {
            if (mIsRecordingVideo) {
                mMediaRecorder.stop();      //Sometimes I get an error message here, why? Maybe I cannot call the stop() if it is not recording.
                mIsRecordingVideo = false;
            }
        } catch (Exception e) {

        }
        closeCamera();
        stopThreads();
        disconnectSockets();
        status = false;
//        recorder.release();
//        recorder = null;
//        Log.d("VS","Recorder released");


        super.onPause();
//        mRobotAPI.robot.setExpression(RobotFace.DEFAULT);
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

    @Override
    protected void onStop()
    {
        super.onStop();
    }

//    /**
//     * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
//     */
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.M)
    private void openCamera() {
        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(30000, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];    //Chih-Yuan Yang 2024/6/16: Use the first camera, and Zenbo has only 1 camera
            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            //Chih-Yuan Yang 2024/6/16: The map object is not used anywhere else.

            mMediaRecorder = new MediaRecorder();
            // 4/25/2018 Chih-Yuan: The permission check is done in the TrackActivity.java
            manager.openCamera(cameraId, mStateCallback, handlerImageListener);
        } catch (final CameraAccessException e) {
            LOGGER.e(e, "Exception!");
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != mPreviewSession) {
                mPreviewSession.close();
                mPreviewSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mPreviewReader) {
                mPreviewReader.close();
                mPreviewReader = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        long max_filesize_bytes = 4 * 1024 * 1024 * 1024;  //4Gib
        mMediaRecorder.setMaxFileSize(max_filesize_bytes);      //Does it work?
        mVideoAbsolutePath = getVideoFilePath(this);
        mMediaRecorder.setOutputFile(mVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    closeCamera();
                    openCamera();
                }
            }
        });
        mMediaRecorder.prepare();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startThreads() {
        threadImageListener = new HandlerThread("ImageListener");
        threadImageListener.start();
        handlerImageListener = new Handler(threadImageListener.getLooper());

        threadSendToServer = new HandlerThread("threadSendToServer");
        threadSendToServer.start();
        handlerSendToServer = new Handler(threadSendToServer.getLooper());

        mThreadAction = new HandlerThread("ActionThread");
        mThreadAction.start();
        mHandlerAction = new Handler(mThreadAction.getLooper());

        mThreadSendAudio = new HandlerThread(("threadSendAudio"));
        mThreadSendAudio.start();
        mHandlerSendAudio = new Handler(mThreadSendAudio.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopThreads() {
        threadImageListener.quitSafely();
        threadSendToServer.quitSafely();
        mThreadAction.quitSafely();
        mThreadSendAudio.quitSafely();

        try {
            threadImageListener.join();
            threadImageListener = null;
            handlerImageListener = null;

            threadSendToServer.join();
            threadSendToServer = null;
            handlerSendToServer = null;

            mThreadAction.join();
            mThreadAction = null;
            mHandlerAction = null;

            mThreadSendAudio.join();
            mThreadSendAudio = null;
            mHandlerSendAudio = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    private void disconnectSockets()
    {
        try {
            if( mSocketSendImages != null) {
                mSocketSendImages.close();
                if (mSocketSendImages.isClosed())
                    mSocketSendImages = null;
            }

            if( mSocketReceiveResults != null) {
                mSocketReceiveResults.close();
                if (mSocketReceiveResults.isClosed())
                    mSocketReceiveResults = null;
            }

            if( mSocketSendAudio != null) {
                mSocketSendAudio.close();
                if (mSocketSendAudio.isClosed())
                    mSocketSendAudio = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getVideoFilePath(Context context) {
        String path = Environment.getExternalStorageDirectory().toString();
        Date currentTime = Calendar.getInstance().getTime();
        Log.d("getVideoFilePath", mDateFormat.format(currentTime));
        //I have to mkdir the Captures folder.
        File file = new File(path + "/Captures");
        if (!file.exists()) {
            file.mkdirs();
        }
        return path + "/Captures/" + mDateFormat.format(currentTime) + ".mp4";
    }

    //2024/6/25: This function works, and a file will be save in Zenbo's storage. It uses MediaRecorder.
    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Set up Surface for the ImageReader
            //Chih-Yuan Yang 2024/6/16: mPreview Reader is an ImageReader
            mPreviewReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            //Chih-Yuan Yang 2024/6/16: mPreviewListener is an ImageListener
            mPreviewReader.setOnImageAvailableListener(mPreviewListener, handlerImageListener);
            mPreviewBuilder.addTarget(mPreviewReader.getSurface());
            surfaces.add(mPreviewReader.getSurface());

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    mIsRecordingVideo = true;

                    // Start recording
                    //TODO: this statement may cause an exception. Be aware.
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    showToast("Failed");
//                    Activity activity = getActivity();
//                    if (null != activity) {
//                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
//                    }
                }
            }, handlerImageListener);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
        mPreviewListener.initialize(handlerSendToServer, inputView, mActionRunnable);
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            //CaptureRequest.CONTROL_MODE: Overall mode of 3A
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, handlerImageListener);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            //mPreviewBuilder is a CaptureRequest.Builder boject
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            final Surface surface = new Surface(texture);   //2024/6/25 Chih-Yuan: Why do I need 2 Surface objects? One for Capture, another for preview.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);   //First target: mTextureView, to show on the screen

            // Create the reader for the preview frames.
            // 2024/6/25 Chih-Yuan: mPreviewReader is an ImageReader object, which can read images from a Surface object.
            mPreviewReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mPreviewReader.setOnImageAvailableListener(mPreviewListener, handlerImageListener);
            mPreviewBuilder.addTarget(mPreviewReader.getSurface());  //Second target: mPreviewReader, to get the Listener

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface, mPreviewReader.getSurface()),//Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            showToast("Failed");
//                            Activity activity = getActivity();
//                            if (null != activity) {
//                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
//                            }
                        }
                    }, handlerImageListener);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewListener.initialize(handlerSendToServer, inputView, mActionRunnable);
    }

    private byte [] ShortToByte_Twiddle_Method(short [] input)
    {
        int short_index, byte_index;
        int iterations = input.length;

        byte [] buffer = new byte[input.length * 2];

        short_index = byte_index = 0;

        for(/*NOP*/; short_index != iterations; /*NOP*/)
        {
            //if input[short_index] is 10000 = 0x2710. The buffer[0] = 0x10, buffer[1] = 0x27;
            buffer[byte_index]     = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

            ++short_index; byte_index += 2;
        }

        return buffer;
    }
}
