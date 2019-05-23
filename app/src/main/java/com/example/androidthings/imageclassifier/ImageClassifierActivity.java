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

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.androidthings.imageclassifier.classifier.Recognition;
import com.example.androidthings.imageclassifier.classifier.TensorFlowImageClassifier;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 程序主入口 在清单文件中定义
 */
public class ImageClassifierActivity extends Activity implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "ImageClassifierActivity";

    // Matches the images used to train the TensorFlow model
    //匹配用于训练TensorFlow模型的图像
    private static final Size MODEL_IMAGE_SIZE = new Size(224, 224);

    /* */
    /* GPIO按钮用于触发图像捕获的密钥代码 */
    private static final int SHUTTER_KEYCODE = KeyEvent.KEYCODE_CAMERA;

    private ImagePreprocessor mImagePreprocessor;
    private TextToSpeech mTtsEngine;
    private TtsSpeaker mTtsSpeaker;
    private CameraHandler mCameraHandler;
    private TensorFlowImageClassifier mTensorFlowClassifier;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ImageView mImage;
    private TextView mResultText;

    private AtomicBoolean mReady = new AtomicBoolean(false);
    private ButtonInputDriver mButtonDriver;
    private Gpio mReadyLED;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        mImage = findViewById(R.id.imageView);
        mResultText = findViewById(R.id.resultText);

        init();
        CameraHandler.dumpFormatInfo(this);
    }

    //初始化
    private void init() {
        if (isAndroidThingsDevice(this)) {
            initPIO();
        }

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);
    }

    /**
     * This method should only be called when running on an Android Things device.
     * 只有在Android Things设备上运行时才应调用此方法。
     */
    private void initPIO() {
        PeripheralManager pioManager = PeripheralManager.getInstance();
        try {
            mReadyLED = pioManager.openGpio(BoardDefaults.getGPIOForLED());
            mReadyLED.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mButtonDriver = new ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    SHUTTER_KEYCODE);
            mButtonDriver.register();
        } catch (IOException e) {
            mButtonDriver = null;
            /* 无法打开GPIO引脚 */
//            Log.w(TAG, "Could not open GPIO pins", e);
            Log.w(TAG, "无法打开GPIO引脚", e);
        }
    }

    //开启线程，后台初始化
    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            //获得照相机
            mCameraHandler = CameraHandler.getInstance();
            try {
                mCameraHandler.initializeCamera(ImageClassifierActivity.this,
                        mBackgroundHandler, MODEL_IMAGE_SIZE, ImageClassifierActivity.this);
                CameraHandler.dumpFormatInfo(ImageClassifierActivity.this);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            Size cameraCaptureSize = mCameraHandler.getImageDimensions();

            if (cameraCaptureSize != null) {
                mImagePreprocessor =
                        new ImagePreprocessor(cameraCaptureSize.getWidth(), cameraCaptureSize.getHeight(),
                                MODEL_IMAGE_SIZE.getWidth(), MODEL_IMAGE_SIZE.getHeight());

                mTtsSpeaker = new TtsSpeaker();
                mTtsSpeaker.setHasSenseOfHumor(true);
                mTtsEngine = new TextToSpeech(ImageClassifierActivity.this,
                        new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if (status == TextToSpeech.SUCCESS) {
                                    mTtsEngine.setLanguage(Locale.CHINA);
                                    mTtsEngine.setOnUtteranceProgressListener(utteranceListener);
                                    mTtsSpeaker.speakReady(mTtsEngine);
                                    Log.i(TAG, "onInit: status" + "操作成功");
                                } else {
                                    /* “无法打开TTS引擎 忽略文本到语音” */
                                    Log.w(TAG, "Could not open TTS Engine (onInit status=" + status
                                            + "). Ignoring text to speech");
                                    mTtsEngine = null;
                                }
                            }
                        });

                try {
                    mTensorFlowClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this,
                            MODEL_IMAGE_SIZE.getWidth(), MODEL_IMAGE_SIZE.getHeight());
                } catch (IOException e) {
                    /* 无法初始化TFLite分类器 */
                    throw new IllegalStateException("Cannot initialize TFLite Classifier", e);
                }

                setReady(true);
            }


        }
    };

    private Runnable mBackgroundClickHandler = new Runnable() {
        @Override
        public void run() {
            if (mTtsEngine != null) {
                mTtsSpeaker.speakShutterSound(mTtsEngine);
            }
            mCameraHandler.takePicture();
        }
    };

    private UtteranceProgressListener utteranceListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            setReady(false);
        }

        @Override
        public void onDone(String utteranceId) {
            setReady(true);
        }

        @Override
        public void onError(String utteranceId) {
            setReady(true);
        }
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        /* 收到钥匙 */
        Log.d(TAG, "Received key up: " + keyCode);
        if (keyCode == SHUTTER_KEYCODE) {
            startImageCapture();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Invoked when the user taps on the UI from a touch-enabled display
     * 当用户从启用触摸的显示器上点击UI时调用
     */
    public void onShutterClick(View view) {
//        Log.d(TAG, "Received screen tap");
        Log.d(TAG, "收到屏幕点击");
        startImageCapture();
    }

    /**
     * Verify and initiate a new image capture
     * 验证并启动新的图像捕获
     */
    private void startImageCapture() {
        boolean isReady = mReady.get();
//        Log.d(TAG, "Ready for another capture? " + isReady);
        Log.d(TAG, "准备好再次捕获? " + isReady);
        if (isReady) {
            setReady(false);
            mResultText.setText("请稍等。。");
            mBackgroundHandler.post(mBackgroundClickHandler);
        } else {
//            Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
            Log.i(TAG, "对不起，处理还没有完成。几秒钟后再试一次");
        }
    }

    /**
     * Mark the system as ready for a new image capture
     * 将系统标记为准备好进行新的图像捕获
     */
    private void setReady(boolean ready) {
        mReady.set(ready);
        if (mReadyLED != null) {
            try {
                mReadyLED.setValue(ready);
            } catch (IOException e) {
//                Log.w(TAG, "Could not set LED", e);
                Log.w(TAG, "无法设置 LED", e);
            }
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        final Bitmap bitmap;
        try (Image image = reader.acquireNextImage()) {
            bitmap = mImagePreprocessor.preprocessImage(image);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImage.setImageBitmap(bitmap);
            }
        });

        final Collection<Recognition> results = mTensorFlowClassifier.doRecognize(bitmap);
        /* 从Tensorflow获得以下结果 */
//        Log.d(TAG, "Got the following results from Tensorflow: " + results);
        Log.d(TAG, "从Tensorflow获得以下结果: " + results);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (results == null || results.isEmpty()) {
//                    mResultText.setText("I don't understand what I see");
                    mResultText.setText("我不明白这是什么");
                } else {
                    StringBuilder sb = new StringBuilder();
                    Iterator<Recognition> it = results.iterator();
                    int counter = 0;
                    while (it.hasNext()) {
                        Recognition r = it.next();
                        sb.append(r.getTitle());
                        counter++;
                        if (counter < results.size() - 1) {
                            sb.append(", ");
                        } else if (counter == results.size() - 1) {
                            sb.append(" or ");
                        }
                    }
                    mResultText.setText(sb.toString());
                }
            }
        });

        if (mTtsEngine != null) {
            // speak out loud the result of the image recognition
            // 大声说出图像识别的结果
            mTtsSpeaker.speakResults(mTtsEngine, results);
        } else {
            // if theres no TTS, we don't need to wait until the utterance is spoken, so we set
            // 如果没有TTS，我们不需要等到说出话语，所以我们设置
            // to ready right away.
            // 立即准备好。
            setReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mBackgroundThread != null) mBackgroundThread.quit();
        } catch (Throwable t) {
            // close quietly
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;

        try {
            if (mCameraHandler != null) mCameraHandler.shutDown();
        } catch (Throwable t) {
            // close quietly
        }
        try {
            if (mTensorFlowClassifier != null) mTensorFlowClassifier.destroyClassifier();
        } catch (Throwable t) {
            // close quietly
        }
        try {
            if (mButtonDriver != null) mButtonDriver.close();
        } catch (Throwable t) {
            // close quietly
        }

        if (mTtsEngine != null) {
            mTtsEngine.stop();
            mTtsEngine.shutdown();
        }
    }

    /**
     * @return true if this device is running Android Things.
     * 如果此设备运行Android Things。
     * <p>
     * Source: https://stackoverflow.com/a/44171734/112705
     */
    private boolean isAndroidThingsDevice(Context context) {
        // We can't use PackageManager.FEATURE_EMBEDDED here as it was only added in API level 26,
        // and we currently target a lower minSdkVersion
        final PackageManager pm = context.getPackageManager();
        boolean isRunningAndroidThings = pm.hasSystemFeature("android.hardware.type.embedded");
//        Log.d(TAG, "isRunningAndroidThings: " + isRunningAndroidThings);
        Log.d(TAG, "正在运行Android Things:  " + isRunningAndroidThings);
        return isRunningAndroidThings;
    }
}
