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

package com.cg.nightshot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.L;
import static com.cg.nightshot.R.id.Focusbutton;
import static com.cg.nightshot.R.id.ISObutton;
import static com.cg.nightshot.R.id.Shotsbutton;
import static com.cg.nightshot.R.id.Shutterbutton;
import static com.cg.nightshot.R.id.picturebutton;
import static java.lang.Math.abs;

public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    int mPictureCounter=0;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));



        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    //Dati globali
    View view;
    Button ISOButton ;
    Button ShutterButton ;
    Button ShotsButton;
    Button FocusButton;
    HorizontalScrollView ValoriEsposizione,ValoriISO,ValoriShots;
    LinearLayout Valorifocus;
    Integer FlagISO,FlagEsposizione,FlagShots,Flagfocus;
    Range<Long> ExposureRange;
    Range<Integer> ISORange;
    CameraCharacteristics characteristics;
    Boolean ParamButtonsalreadyset;
    Button temp;
    ///parametri camera
    Integer ISO;
    Float Focus;
    Long Exposure;
    Integer Shots;
    TextView Numberview;
    float minimumLens;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            captureStillPicture();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((MainActivity)getActivity()).hideStatusBar();
        ParamButtonsalreadyset=false;
        view = inflater.inflate(R.layout.fragment_camera2_basic, container, false);
        FlagISO=0;
        FlagEsposizione=0;
        FlagShots=0;
        Flagfocus=0;
        Shots=10;
        ISO=100;
        Exposure=100000000L;
        ISOButton = (Button) view.findViewById(R.id.ISObutton);
        ShutterButton = (Button) view.findViewById(R.id.Shutterbutton);
        ShotsButton= (Button) view.findViewById(R.id.Shotsbutton);
        FocusButton = (Button) view.findViewById(R.id.Focusbutton);
        ValoriISO=(HorizontalScrollView) view.findViewById(R.id.ISO_Scroll);
        ValoriISO.setVisibility(view.INVISIBLE);
        ValoriEsposizione=(HorizontalScrollView) view.findViewById(R.id.Exposure_Scroll);
        ValoriEsposizione.setVisibility(view.INVISIBLE);
        ValoriShots=(HorizontalScrollView) view.findViewById(R.id.Shots_Scroll);
        ValoriShots.setVisibility(view.INVISIBLE);
        Valorifocus=(LinearLayout) view.findViewById(R.id.linearlayoutfocus);
        Valorifocus.setVisibility(view.INVISIBLE);
        SeekBar FocusBar=(SeekBar)view.findViewById(R.id.focusbar);
        FocusBar.setProgress(50);
        Numberview=(TextView) view.findViewById(R.id.NumberView);
        Numberview.setVisibility(View.INVISIBLE);

        ISOButton.setOnClickListener(this);
        ShutterButton.setOnClickListener(this);
        ShotsButton.setOnClickListener(this);
        FocusButton.setOnClickListener(this);
        FocusBar.setOnSeekBarChangeListener(new Focusseekbarchangelistener());
        return view;}




    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(picturebutton).setOnClickListener(this);

                mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                 characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                ExposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                ISORange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                minimumLens = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                if (ParamButtonsalreadyset==false){
                AddISObuttons();
                AddExposurebuttons();
                AddShotsbuttons();
                ParamButtonsalreadyset=true;}
                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/20);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        ((MainActivity)getActivity()).showStatusBar();
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            mPreviewRequestBuilder.addTarget(surface);
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                               // mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);


                                // Flash is automatically enabled when necessary.
                                //setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
   /* private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            //mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    } */


    private void captureStillPicture() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF); //flash off
            //mPreviewRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
                        // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            List<CaptureRequest> captureList = new ArrayList<CaptureRequest>();
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            for (int i=0;i<Shots;i++) {
                captureList.add(mPreviewRequestBuilder.build());

            }

            mCaptureSession.stopRepeating();
            mCaptureSession.captureBurst(captureList, cameraCaptureCallback, null);
            mPreviewRequestBuilder.removeTarget(mImageReader.getSurface());

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    CameraCaptureSession.CaptureCallback cameraCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.d("camera","saved");
            Log.i(TAG, "Time saved= " + System.currentTimeMillis());
            mPictureCounter++;
            UpdateCont(mPictureCounter);
           getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Numberview.setText(Integer.toString(mPictureCounter));
                }
            });
            if (mPictureCounter >= Shots)
            { Log.d("camera","finito");
                unlockFocus();

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Numberview.setText("0");
                        Numberview.setVisibility(View.INVISIBLE);
                    }
                });
                mPictureCounter=0;
                ((MainActivity)getActivity()).Esecuzione();
               }

        }
    };

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        Context context= this.getActivity();

        switch (view.getId()) {
            case picturebutton: {
                takePicture();
                Animation shake = AnimationUtils.loadAnimation(context, R.anim.shake);
                view.startAnimation(shake);
                mPictureCounter=0;
                Numberview.setVisibility(View.VISIBLE);

                break;
            }
            case ISObutton:{
                ISOButton.setTextColor(getResources().getColor(R.color.colorPrimary));
                ShotsButton.setTextColor(Color.WHITE);
                FocusButton.setTextColor(Color.WHITE);
                ShutterButton.setTextColor(Color.WHITE);

                if (Flagfocus==1){
                    Valorifocus.setVisibility(view.INVISIBLE);
                    Flagfocus=0;
                    FocusButton.setTextColor(Color.WHITE);}

                if (FlagEsposizione==1){
                    ValoriEsposizione.setVisibility(view.INVISIBLE);
                    FlagEsposizione=0;
                    ShutterButton.setTextColor(Color.WHITE);
                }
                if (FlagShots==1){
                    ValoriShots.setVisibility(view.INVISIBLE);
                    FlagShots=0;
                    ShotsButton.setTextColor(Color.WHITE);
                }
                switch(FlagISO){
                    case 1:
                        ValoriISO.setVisibility(view.INVISIBLE);
                        FlagISO=0;
                        ISOButton.setTextColor(Color.WHITE);
                        break;
                    case 0:
                        ValoriISO.setVisibility(view.VISIBLE);
                        FlagISO=1;
                        break;}
                break;
            }
            case Shotsbutton:{
                ShotsButton.setTextColor(getResources().getColor(R.color.colorPrimary));
                FocusButton.setTextColor(Color.WHITE);
                ShutterButton.setTextColor(Color.WHITE);
                ISOButton.setTextColor(Color.WHITE);
                if (Flagfocus==1){
                    Valorifocus.setVisibility(view.INVISIBLE);
                    Flagfocus=0;
                    FocusButton.setTextColor(Color.WHITE);}

                if (FlagEsposizione==1){
                    ValoriEsposizione.setVisibility(view.INVISIBLE);
                    FlagEsposizione=0;
                    ShutterButton.setTextColor(Color.WHITE);
                }
                if (FlagISO==1){
                    ValoriISO.setVisibility(view.INVISIBLE);
                    FlagISO=0;
                    ISOButton.setTextColor(Color.WHITE);}

                switch(FlagShots){
                    case 1:
                        ValoriShots.setVisibility(view.INVISIBLE);
                        FlagShots=0;
                        ShotsButton.setTextColor(Color.WHITE);
                        break;
                    case 0:
                        ValoriShots.setVisibility(view.VISIBLE);
                        FlagShots=1;
                        break;}

                break;}

            case Focusbutton:{
                FocusButton.setTextColor(getResources().getColor(R.color.colorPrimary));
                ShutterButton.setTextColor(Color.WHITE);
                ISOButton.setTextColor(Color.WHITE);
                ShotsButton.setTextColor(Color.WHITE);
                if (FlagEsposizione==1){
                    ValoriEsposizione.setVisibility(view.INVISIBLE);
                    FlagEsposizione=0;
                    ShutterButton.setTextColor(Color.WHITE);
                }
                if (FlagShots==1){
                    ValoriShots.setVisibility(view.INVISIBLE);
                    FlagShots=0;
                    ShotsButton.setTextColor(Color.WHITE);
                }

                if (FlagISO==1){
                    ValoriISO.setVisibility(view.INVISIBLE);
                    FlagISO=0;
                    ShotsButton.setTextColor(Color.WHITE);}

                switch(Flagfocus){
                    case 1:
                        Valorifocus.setVisibility(view.INVISIBLE);
                        Flagfocus=0;
                        ISOButton.setTextColor(Color.WHITE);
                        break;
                    case 0:
                        Valorifocus.setVisibility(view.VISIBLE);
                        Flagfocus=1;
                        break;}


                break;
            }
            case Shutterbutton:{
                ShutterButton.setTextColor(getResources().getColor(R.color.colorPrimary));
                ISOButton.setTextColor(Color.WHITE);
                ShotsButton.setTextColor(Color.WHITE);
                FocusButton.setTextColor(Color.WHITE);

                if (Flagfocus==1){
                    Valorifocus.setVisibility(view.INVISIBLE);
                    Flagfocus=0;
                    FocusButton.setTextColor(Color.WHITE);}

                if (FlagISO==1){
                    ValoriISO.setVisibility(view.INVISIBLE);
                    FlagISO=0;
                    ISOButton.setTextColor(Color.WHITE);}

                if (FlagShots==1){
                    ValoriShots.setVisibility(view.INVISIBLE);
                    FlagShots=0;
                    ShotsButton.setTextColor(Color.WHITE);
                }
                switch(FlagEsposizione){
                    case 1:
                        ValoriEsposizione.setVisibility(view.INVISIBLE);
                        FlagEsposizione=0;
                        ShutterButton.setTextColor(Color.WHITE);
                        break;
                    case 0:
                        ValoriEsposizione.setVisibility(view.VISIBLE);
                        FlagEsposizione=1;
                        break;}
                        break;
            }

        }}



    /*private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
        }
    }*/

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private  class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        public  File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File myDir = new File(root + "/DCIM/NightShot/temp");
            myDir.mkdirs();
            Calendar c= Calendar.getInstance();
            SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
            String data=sdf.format(c.getTime());
            mFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ "/DCIM/NightShot/temp/", "IMG"+data+".jpg");
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }
    public void AddISObuttons(){
        Context context= this.getActivity();
        int ISOLabel=ISORange.getLower();
        Button btn1;
        LinearLayout ISOlayout = (LinearLayout)view.findViewById(R.id.linearlayoutiso);
        if (ISOLabel<=50)
            ISOLabel=50;
        else if(ISOLabel<=100)
            ISOLabel=100;
        int counter=1;
        while(ISOLabel<=ISORange.getUpper()) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            Button btn = new Button(context);
            btn.setId(counter);
            final int id_ = btn.getId();
            btn.setText(Integer.toString(ISOLabel));
            btn.setBackgroundColor(00000000);
            btn.setTextColor(Color.WHITE);
            btn.setWidth(40);
            btn.setAllCaps(false);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if (temp!=null)
                        temp.setTextColor(Color.WHITE);
                    temp=(Button)view.findViewById(id_);
                    temp.setTextColor(getResources().getColor(R.color.colorPrimary));
                    ISO=Integer.parseInt(temp.getText().toString());
                    Log.d("ISO",ISO.toString());
                    Log.d("Exposure",Exposure.toString());
                    Log.d("Focus",Focus.toString());

                    // mPreviewRequest = mPreviewRequestBuilder.build();
                   // createCameraPreviewSession();

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO);
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Exposure);
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);
                    mPreviewRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE,CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);


                }
            });
            ISOlayout.addView(btn, params);
            counter++;
           ISOLabel=ISOLabel*2;

        }}



    public void AddExposurebuttons(){
        Context context= this.getActivity();
        Long ExposureLabel=ExposureRange.getLower();
       if (ExposureLabel<=1000000L)
          ExposureLabel= 1000000L;
       else if (ExposureLabel<=2000000L)
           ExposureLabel=2000000L;
       else if (ExposureLabel<=4000000L)
           ExposureLabel=4000000L;


        Button btn1;
        LinearLayout ExposureLayout = (LinearLayout)view.findViewById(R.id.linearlayoutexposure);
        int counter=1;
        //Long costant=0.000000001;
        while(ExposureLabel<=ExposureRange.getUpper()) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            Button btn = new Button(context);
            btn.setId(counter);
            final int id_ = btn.getId();
            if (ExposureLabel==1000000L)
                btn.setText("1/1000s");
            else if (ExposureLabel==2000000L)
                btn.setText("1/500s");
            else if (ExposureLabel==4000000L)
                btn.setText("1/250s");
            else if (ExposureLabel==8000000L)
                btn.setText("1/125s");
            else if (ExposureLabel==16666667L)
                btn.setText("1/60s");
            else if (ExposureLabel==33333334L)
                btn.setText("1/30s");
            else if (ExposureLabel==66666668L)
                btn.setText("1/15s");
            else if (ExposureLabel==125000000L)
                btn.setText("1/8s");
            else if (ExposureLabel==250000000L)
                btn.setText("1/4s");
            else if (ExposureLabel==500000000L)
                btn.setText("1/2s");
            else
            btn.setText(Long.toString(ExposureLabel/1000000000L)+"s");
            btn.setBackgroundColor(00000000);
            btn.setTextColor(Color.WHITE);
            btn.setWidth(40);
            btn.setAllCaps(false);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if (temp!=null)
                    temp.setTextColor(Color.WHITE);
                    temp=(Button)view.findViewById(id_);
                    temp.setTextColor(getResources().getColor(R.color.colorPrimary));
                    switch(temp.getText().toString()){
                        case "1/1000s":
                            Exposure=((long)(0.001*1000000000));
                            break;

                        case "1/500s":
                            Exposure=((long)(0.002*1000000000));
                            break;

                        case "1/250s":
                            Exposure=((long)(0.004*1000000000));
                            break;

                        case "1/125s":
                            Exposure=((long)(0.008*1000000000));
                            break;

                        case "1/60s":
                            Exposure=16666666l;
                            break;

                        case "1/30s":
                            Exposure=33333334l;
                            break;

                        case "1/15s":
                            Exposure=66666668l;
                            break;

                        case "1/8s":
                            Exposure=125000000l;
                            break;

                        case "1/4s":
                            Exposure=250000000l;
                            break;

                        case "1/2s":
                            Exposure=500000000l;
                            break;

                        default:
                           Exposure= (long)Integer.parseInt(temp.getText().toString().substring(0, temp.getText().toString().length() - 1))*1000000000;}
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Exposure);
                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,ISO);
                    mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,Focus);

                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);
                    mPreviewRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE,CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
                    Log.d("ISO",ISO.toString());
                    Log.d("Exposure",Exposure.toString());
                    Log.d("Focus",Focus.toString());

                    //mPreviewRequest = mPreviewRequestBuilder.build();
                    //createCameraPreviewSession();

                }
            });
            ExposureLayout.addView(btn, params);
            counter++;
            if(ExposureLabel==8000000L)
                ExposureLabel=16666667L;
            else if(ExposureLabel==66666668)
                ExposureLabel=125000000L;
            else
            ExposureLabel=ExposureLabel*2;

        }}
    private class Focusseekbarchangelistener implements SeekBar.OnSeekBarChangeListener {

        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            // Log the progress
            //Log.d("DEBUG", "Progress is: "+maxLens);
            //set textView's text

             Focus = (abs((float)progress-100) * minimumLens / 100);
            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,Focus);
            try{ mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {

               e.printStackTrace();
        }}

        public void onStartTrackingTouch(SeekBar seekBar) {

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        }

        public void onStopTrackingTouch(SeekBar seekBar) {}

    }





    public void AddShotsbuttons(){
        Context context= this.getActivity();
        int ShotsLabel=5;
        LinearLayout Shotslayout = (LinearLayout)view.findViewById(R.id.linearlayoutshots);
        int counter=1;
        while(ShotsLabel<=100) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            Button btn = new Button(context);
            btn.setId(counter);
            final int id_ = btn.getId();
            btn.setText(Integer.toString(ShotsLabel));
            btn.setBackgroundColor(00000000);
            btn.setTextColor(Color.WHITE);
            btn.setWidth(40);
            btn.setAllCaps(false);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if (temp!=null)
                        temp.setTextColor(Color.WHITE);
                    temp=(Button)view.findViewById(id_);
                    temp.setTextColor(getResources().getColor(R.color.colorPrimary));
                    Shots=Integer.parseInt(temp.getText().toString());

                    // mPreviewRequest = mPreviewRequestBuilder.build();
                    // createCameraPreviewSession();

                }
            });
            Shotslayout.addView(btn, params);
            counter++;
            if (ShotsLabel<10)
                ShotsLabel=ShotsLabel+1;
            else if (ShotsLabel<50)
                ShotsLabel=ShotsLabel+5;
            else
            ShotsLabel=ShotsLabel+10;

        }}

    public void UpdateCont(int N){

    }}
