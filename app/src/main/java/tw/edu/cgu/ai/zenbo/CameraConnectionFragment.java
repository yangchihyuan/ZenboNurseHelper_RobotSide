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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
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
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.asus.robotframework.API.RobotAPI;

import tw.edu.cgu.ai.zenbo.env.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


//public class CameraConnectionFragment extends Fragment implements View.OnClickListener {
public class CameraConnectionFragment extends Fragment {
    private static final Logger LOGGER = new Logger();

    private KeyPointView keypointView;
    private InputView inputView;
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
    private com.asus.robotframework.API.RobotAPI ZenboAPI;
    private com.asus.robotframework.API.SpeakConfig speakConfig;
    private DataBuffer m_DataBuffer;
    private MediaRecorder mMediaRecorder;
    private String mVideoAbsolutePath;
    private Size mPreviewSize = new Size(640, 480);
    private CameraDevice mCameraDevice;
    private HandlerThread threadImageListener;
    private Handler handlerImageListener;
    private HandlerThread threadSendToServer;
    private Handler handlerSendToServer;
    private HandlerThread mActionThread;
    private Handler mActionHandler;
    private ImageReader mPreviewReader;     //used to get onImageAvailable, not used in Camera2Video project
    private CaptureRequest.Builder mPreviewBuilder;
    //    private CaptureRequest mPreviewRequest;
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
    Socket socket_result;
    Socket socket;

    java.util.Timer timer_get_analyzed_results;
    java.util.Timer timer_check_disconnection;      //I need a timer to check whether the connection is lost.
    InputStreamReader mInputStreamReader;
    BufferedReader mBufferedReader_inFromServer;

    TimerTask task_get_analyzed_results = new TimerTask() {
        public void run() {
            Log.d("CameraConnection", "into task_get_analyzed_results");
            final long timestamp_start = System.currentTimeMillis();
            String result = "";
            try {
                if( mBufferedReader_inFromServer != null) {
                    String line;
                    while (true)
                    {
                        line = mBufferedReader_inFromServer.readLine();
                        //if the server is down, the line will be null, and there will be an exception.
                        if( line.equals("EndToken"))
                            break;
                        result += (line + "\n");
                    }
                }
                if (!result.isEmpty()) {
                    m_DataBuffer.AddNewFrame(result);
                    mActionHandler.post(mActionRunnable);
                    keypointView.setResults(m_DataBuffer.getLatestFrame());
                }
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
            }
            final long timestamp_end = System.currentTimeMillis();
        }
    };


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
                    if( bRecordVideo == false )
                        startPreview();
                    else
                        //Chih-Yuan Yang 2024/6/16: This is the reason I never record videos. I set the boolean variable fixed.
                        startRecordingVideo();      //The startRecordingVideo is called in a callback function. Is it fine?
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
                    final Activity activity = getActivity();
                    if (null != activity) {
                        activity.finish();
                    }
                }
            };

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public static CameraConnectionFragment newInstance() {
        return new CameraConnectionFragment();
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.camera_connection_fragment, container, false);
        return v;
    }


    //Chih-Yuan Yang: onViewCreated is a member function of Fragment
    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        inputView = (InputView) view.findViewById(R.id.inputview);
        keypointView = (KeyPointView) view.findViewById(R.id.keypoint);
        editText_Port = (EditText) view.findViewById(R.id.editText_Port);
        editText_Server = (EditText) view.findViewById(R.id.editText_Server);
        checkBox_keep_alert = (CheckBox) view.findViewById(R.id.checkBox_keepalert);
        checkBox_enable_connection = (CheckBox) view.findViewById(R.id.checkBox_connect);
        checkBox_show_face = (CheckBox) view.findViewById(R.id.checkBox_ShowFace);
        checkBox_dont_move = (CheckBox) view.findViewById(R.id.checkBox_DontMove);
        checkBox_dont_rotate = (CheckBox) view.findViewById(R.id.checkBox_DontRotate);
        mMessageView_Detection = (MessageView) view.findViewById(R.id.MessageView_Detection);
        mMessageView_Timestamp = (MessageView) view.findViewById(R.id.MessageView_Timestamp);
        ZenboAPI = new RobotAPI(view.getContext());
        button_close = (Button) view.findViewById(R.id.button_close);
//        speakConfig = new SpeakConfig();
//        speakConfig.languageId(SpeakConfig.LANGUAGE_ID_EN_US);   //Does this matter?

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
                    ZenboAPI.release();
                    mActionRunnable.ZenboAPI.release();
                    getActivity().finish();
                    getActivity().moveTaskToBack(true);
                }
            }
        );

        TimerTask task_check_connection = new TimerTask() {
            public void run() {
                if (checkBox_enable_connection.isChecked()) {
                    if( socket == null )
                    {
                        try {
                            String ServerURL = editText_Server.getText().toString();
                            int PortNumber = Integer.parseInt(editText_Port.getText().toString());
                            socket = new Socket(ServerURL, PortNumber);
                            socket_result = new Socket(ServerURL, PortNumber + 1);
                            if (socket_result.isConnected()) {
                                mInputStreamReader = new InputStreamReader(socket_result.getInputStream());
                                mBufferedReader_inFromServer = new BufferedReader(mInputStreamReader);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if( socket != null ) {
                            if (socket.isConnected() && socket_result.isConnected()) {
                                showToast("Server Connected ");
                                mPreviewListener.set_socket(socket);
                            }
                        }
                    }
                    else   //if socket != null
                    {
                        if (mPreviewListener.mbSendSuccessfully == false) {
                            try {
                                if( socket != null) {
                                    socket.close();
                                    if (socket.isClosed())
                                        socket = null;
                                }

                                if( socket_result != null) {
                                    socket_result.close();
                                    if (socket_result.isClosed())
                                        socket_result = null;
                                }

                                String ServerURL = editText_Server.getText().toString();
                                int PortNumber = Integer.parseInt(editText_Port.getText().toString());
                                //Chih-Yuan Yang 2024/6/20: I repeatedly build and destroy the
                                //socket because I cannot guarantee Zenbo can successfully access the WiFi.
                                socket = new Socket(ServerURL, PortNumber);
                                socket_result = new Socket(ServerURL, PortNumber + 1);
                                if (socket_result.isConnected()) {
                                    mInputStreamReader = new InputStreamReader(socket_result.getInputStream());
                                    mBufferedReader_inFromServer = new BufferedReader(mInputStreamReader);
                                }

                                if (socket.isConnected() && socket_result.isConnected()) {
                                    showToast("Server Connected ");
                                    mPreviewListener.set_socket(socket);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                else  //checkBox_enable_connection.isChecked() is false
                {
                    try {
                        if (socket != null) {
                            socket.close();
                            if (socket.isClosed())
                                socket = null;
                            showToast("Server Disonnected ");
                        }

                        if (socket_result != null) {
                            socket_result.close();
                            if (socket_result.isClosed())
                                socket_result = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

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
        long delay = 1000;
        long period = 33;        //33 ms
        timer_get_analyzed_results.schedule(task_get_analyzed_results, delay, period);

        timer_check_disconnection = new java.util.Timer(true);
        timer_check_disconnection.schedule(task_check_connection, 1000, 1000);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        startthreadImageListener();
//        button_run.requestFocus();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

    @Override
    public void onPause() {
        try {
            if (mIsRecordingVideo) {
                mMediaRecorder.stop();      //Sometimes I get an error message here, why? Maybe I cannot call the stop() if it is not recording.
                mIsRecordingVideo = false;
            }
        } catch (Exception e) {

        }
        closeCamera();
        stopthreadImageListener();
        super.onPause();
    }

    /**
     * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
     */
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.M)
    private void openCamera() {
        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
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
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        long max_filesize_bytes = 4 * 1024 * 1024 * 1024;  //4Gib
        mMediaRecorder.setMaxFileSize(max_filesize_bytes);      //Does it work?
        mVideoAbsolutePath = getVideoFilePath(getActivity());
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
    private void startthreadImageListener() {
        threadImageListener = new HandlerThread("ImageListener");
        threadImageListener.start();
        handlerImageListener = new Handler(threadImageListener.getLooper());

        threadSendToServer = new HandlerThread("threadSendToServer");
        threadSendToServer.start();
        handlerSendToServer = new Handler(threadSendToServer.getLooper());

        mActionThread = new HandlerThread("ActionThread");
        mActionThread.start();
        mActionHandler = new Handler(mActionThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopthreadImageListener() {
        threadImageListener.quitSafely();
        threadSendToServer.quitSafely();
        mActionThread.quitSafely();
        try {
            threadImageListener.join();
            threadImageListener = null;
            handlerImageListener = null;

            threadSendToServer.join();
            threadSendToServer = null;
            handlerSendToServer = null;

            mActionThread.join();
            mActionThread = null;
            mActionHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
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
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
//                            mButtonVideo.setText(R.string.stop);
                            mIsRecordingVideo = true;

                            // Start recording
                            //TODO: this statement may cause an exception. Be aware.
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
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
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            final Surface surface = new Surface(texture);
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            // Create the reader for the preview frames.
            mPreviewReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mPreviewReader.setOnImageAvailableListener(mPreviewListener, handlerImageListener);
            mPreviewBuilder.addTarget(mPreviewReader.getSurface());

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
                            Activity activity = getActivity();
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, handlerImageListener);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewListener.initialize(handlerSendToServer, inputView, mActionRunnable);
    }
}
