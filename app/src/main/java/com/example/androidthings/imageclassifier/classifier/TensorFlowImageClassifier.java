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
package com.example.androidthings.imageclassifier.classifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.List;

/**
 * A classifier specialized to label images using TensorFlow.
 * 专门用于使用TensorFlow标记图像的分类器。
 */
public class TensorFlowImageClassifier {

    private static final String TAG = "TFImageClassifier";

    private static final String LABELS_FILE = "labels.txt";
    private static final String MODEL_FILE = "mobilenet_quant_v1_224.tflite";

    /** Dimensions of inputs. */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;

    /** Labels for categories that the TensorFlow model is trained for.TensorFlow模型训练的类别标签。 */
    private List<String> labels;

    /** Cache to hold image data. 缓存以保存图像数据 */
    private ByteBuffer imgData = null;

    /** Inference results (Tensorflow Lite output).
     *  推理结果（Tensorflow Lite输出）。*/
    private byte[][] confidencePerLabel = null;

    /** Pre-allocated buffer for intermediate bitmap pixels
     *  为中间位图像素预先分配的缓冲区*/
    private int[] intValues;

    /** TensorFlow Lite engine
     * TensorFlow Lite引擎 */
    private Interpreter tfLite;

    /**
     * Initializes a TensorFlow Lite session for classifying images.
     * 初始化TensorFlow Lite会话以对图像进行分类。
     */
    public TensorFlowImageClassifier(Context context, int inputImageWidth, int inputImageHeight)
            throws IOException {
        this.tfLite = new Interpreter(TensorFlowHelper.loadModelFile(context, MODEL_FILE));
        this.labels = TensorFlowHelper.readLabels(context, LABELS_FILE);

        imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE * inputImageWidth * inputImageHeight * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        confidencePerLabel = new byte[1][labels.size()];

        // Pre-allocate buffer for image pixels.
        intValues = new int[inputImageWidth * inputImageHeight];
    }

    /**
     * Clean up the resources used by the classifier.
     */
    public void destroyClassifier() {
        tfLite.close();
    }


    /**
     * @param image Bitmap containing the image to be classified. The image can be
     *              of any size, but preprocessing might occur to resize it to the
     *              format expected by the classification process, which can be time
     *              and power consuming.
     *              包含要分类的图像的位图。图像可以是任何大小的*，但可能会进行预处理以将其调整为分类过程所期望的*格式，
     *              这可能是消耗更多时间和电量。
     */
    public Collection<Recognition> doRecognize(Bitmap image) {
        TensorFlowHelper.convertBitmapToByteBuffer(image, intValues, imgData);

        long startTime = SystemClock.uptimeMillis();
        // Here's where the magic happens!!!
        tfLite.run(imgData, confidencePerLabel);
        long endTime = SystemClock.uptimeMillis();
//        Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));
        Log.d(TAG, "时间成本运行模型推理: " + Long.toString(endTime - startTime));

        // Get the results with the highest confidence and map them to their labels
        return TensorFlowHelper.getBestResults(confidencePerLabel, labels);
    }

}


