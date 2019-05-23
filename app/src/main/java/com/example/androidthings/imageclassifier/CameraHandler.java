/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.imageclassifier;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class CameraHandler {
    private static final String TAG = CameraHandler.class.getSimpleName();

    private static final int MAX_IMAGES = 1;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private boolean initialized;

    private Size mImageDimensions;

    /**
     * An {@link ImageReader} that handles still image capture.
     * 处理静止图像捕获。
     */
    private ImageReader mImageReader;

    // Lazy-loaded singleton, so only one instance of the camera is created.
    private CameraHandler() {
    }

    private static class InstanceHolder {
        private static CameraHandler mCamera = new CameraHandler();
    }

    public static CameraHandler getInstance() {
        return InstanceHolder.mCamera;
    }

    /**
     * Initialize the camera device
     * 初始化相机设备
     */
    @SuppressLint("MissingPermission")
    public void initializeCamera(Context context, Handler backgroundHandler, Size minSize,
                                 ImageReader.OnImageAvailableListener imageAvailableListener)
        throws CameraAccessException {
        if (initialized) {
//            throw new IllegalStateException("CameraHandler is already initialized or is initializing");
            throw new IllegalStateException("CameraHandler已初始化或正在初始化");
        }
        initialized = true;
        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String camId = getCameraId(context);

        if (camId!=null){
            // Initialize the image processor with the largest available size.
            assert manager != null;
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size bestSize = getBestCameraSize(map.getOutputSizes(ImageFormat.JPEG), minSize);
            if (bestSize == null) {
//                throw new RuntimeException("We could not find a camera resolution that is larger than " + minSize.getWidth() + "x" + minSize.getHeight());
                throw new RuntimeException("我们找不到大于的相机分辨率 " + minSize.getWidth() + "x" + minSize.getHeight());
            }

            mImageReader = ImageReader.newInstance(bestSize.getWidth(), bestSize.getHeight(),
                    ImageFormat.JPEG, MAX_IMAGES);
            mImageDimensions = bestSize;
//            Log.d(TAG, "Will capture photos that are " + mImageDimensions.getWidth() + " x " +
            Log.d(TAG, "将拍摄照片 " + mImageDimensions.getWidth() + " x " +
                    mImageDimensions.getHeight());
            mImageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            // Open the camera resource
            try {
                manager.openCamera(camId, mStateCallback, backgroundHandler);
            } catch (CameraAccessException cae) {
//                Log.e(TAG, "Camera access exception", cae);
                Log.e(TAG, "相机访问异常", cae);
            }
        }



    }

    public Size getImageDimensions() {
        return mImageDimensions;
    }

    /**
     * Begin a still image capture
     * 开始拍摄静止图像
     */
    public void takePicture() {
        if (mCameraDevice == null) {
//            Log.w(TAG, "Cannot capture image. Camera not initialized.");
            Log.w(TAG, "无法捕捉图像。相机未初始化");
            return;
        }
        // Create a CameraCaptureSession for capturing still images.
        try {
            mCameraDevice.createCaptureSession(
                    Collections.singletonList(mImageReader.getSurface()),
                    mSessionCallback,
                    null);
        } catch (CameraAccessException cae) {
//            Log.e(TAG, "Cannot create camera capture session", cae);
            Log.e(TAG, "无法创建摄像头捕获会话", cae);
        }
    }

    /**
     * Execute a new capture request within the active session
     * 在活动会话中执行新的捕获请求
     */
    private void triggerImageCapture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//            Log.d(TAG, "Capture request created.");
            Log.d(TAG, "捕获请求已创建");
            mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException cae) {
//            Log.e(TAG, "Cannot trigger a capture request");
            Log.e(TAG, "无法触发捕获请求");
        }
    }

    private void closeCaptureSession() {
        if (mCaptureSession != null) {
            try {
                mCaptureSession.close();
            } catch (Exception ex) {
                Log.w(TAG, "无法关闭捕获会话", ex);
//                Log.w(TAG, "Could not close capture session", ex);
            }
            mCaptureSession = null;
        }
    }

    /**
     * Close the camera resources
     * 关闭相机资源
     */
    public void shutDown() {
        try {
            closeCaptureSession();
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mImageReader.close();
        } finally {
            initialized = false;
        }
    }

    public static String getCameraId(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String[] camIds = null;
        try {
            camIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
//            Log.w(TAG, "Cannot get the list of available cameras", e);
            Log.w(TAG, "无法获取可用摄像头列表", e);
        }
        if (camIds == null || camIds.length < 1) {
//            Log.d(TAG, "No cameras found");
            Log.d(TAG, "没有找到相机");
            return null;
        }
        return camIds[0];
    }

    /**
     * Helpful debugging method:  Dump all supported camera formats to log.  You don't need to run
     * this for normal operation, but it's very helpful when porting this code to different
     * hardware.
     *
     * 有用的调试方法：转储所有支持的相机格式以进行记录。您不需要为正常操作运行* this，但在将此代码移植到不同的*硬件时非常有用。
     */
    public static void dumpFormatInfo(Context context) {
        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String camId = getCameraId(context);
        if (camId == null) {
            return;
        }
//        Log.d(TAG, "Using camera id " + camId);
        Log.d(TAG, "使用相机ID " + camId);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            for (int format : configs.getOutputFormats()) {
//                Log.d(TAG, "Getting sizes for format: " + format);
                Log.d(TAG, "获取格式的大小: " + format);
                for (Size s : configs.getOutputSizes(format)) {
                    Log.d(TAG, "\t" + s.toString());
                }
            }
            int[] effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
            for (int effect : effects) {
//                Log.d(TAG, "Effect available: " + effect);
                Log.d(TAG, "效果可用: " + effect);
            }
        } catch (CameraAccessException e) {
//            Log.e(TAG, "Camera access exception getting characteristics.");
            Log.e(TAG, "相机访问异常获取特征.");
        }
    }


    /**
     * Callback handling device state changes
     * 回调处理设备状态更改
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
//            Log.d(TAG, "Opened camera.");
            Log.d(TAG, "打开相机.");
            mCameraDevice = cameraDevice;
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
//            Log.d(TAG, "Camera disconnected, closing.");
            Log.d(TAG, "相机断开，关闭.");
            closeCaptureSession();
            cameraDevice.close();
        }
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
//            Log.d(TAG, "Camera device error, closing.");
            Log.d(TAG, "相机设备错误，关闭.");
            closeCaptureSession();
            cameraDevice.close();
        }
        @Override
        public void onClosed(@NonNull CameraDevice cameraDevice) {
//            Log.d(TAG, "Closed camera, releasing");
            Log.d(TAG, "关闭相机，释放");
            mCameraDevice = null;
        }
    };

    /**
     * Callback handling session state changes
     * 回调处理会话状态更改
     */
    private CameraCaptureSession.StateCallback mSessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (mCameraDevice == null) {
                        return;
                    }
                    // When the session is ready, we start capture.
                    mCaptureSession = cameraCaptureSession;
                    triggerImageCapture();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    Log.w(TAG, "Failed to configure camera");
                    Log.w(TAG, "无法配置摄像头");
                }
            };

    /**
     * Callback handling capture session events
     * 回调处理捕获会话事件
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
//                    Log.d(TAG, "Partial result");
                    Log.d(TAG, "部分结果");
                }
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    session.close();
                    mCaptureSession = null;
//                    Log.d(TAG, "CaptureSession closed");
                    Log.d(TAG, "捕获会话已关闭");
                }
            };

    static Size getBestCameraSize(Size[] availableCameraResolutions, Size minSize) {
        // This should select the closest size that is not too small
        Arrays.sort(availableCameraResolutions, new CompareSizesByArea()); // Sort by smallest first
        for (Size resolution : availableCameraResolutions) {
            if (resolution.getWidth() >= minSize.getWidth() &&
                    resolution.getHeight() >= minSize.getHeight()) {
                return resolution;
            }
        }
        return null;
    }


    /**
     * Compares two {@code Size}s based on their areas ascending.
     * 根据提升的区域比较两个
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}