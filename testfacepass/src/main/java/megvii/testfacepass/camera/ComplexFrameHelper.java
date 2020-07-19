package megvii.testfacepass.camera;

import android.util.Log;
import android.util.Pair;

import java.util.concurrent.ArrayBlockingQueue;


public class ComplexFrameHelper {

    private static ArrayBlockingQueue<Pair<CameraPreviewData, CameraPreviewData>> complexFrameQueue
            = new ArrayBlockingQueue<>(2);
    private static CameraPreviewData rgbFrameBuffer = null;
    private static CameraPreviewData irFrameBuffer = null;
    private static void makeComplexFrame() {//Log.i("FeedFrameThread", "addRgbFrame4");
        if ((rgbFrameBuffer != null) && (irFrameBuffer != null)) {
//            Log.i("FeedFrameThread", "remainingCapacity:" + complexFrameQueue.remainingCapacity());
            if (complexFrameQueue.remainingCapacity() > 0) {
                complexFrameQueue.offer(new Pair(rgbFrameBuffer, irFrameBuffer));
//                Log.i("FeedFrameThread", "size:" + complexFrameQueue.size());
            }
            rgbFrameBuffer = null;
            irFrameBuffer = null;
        }
    }

    public static void addRgbFrame(CameraPreviewData rgbFrame) {
        synchronized (ComplexFrameHelper.class) {
//            Log.i("FeedFrameThread", "addRgbFrame2");
            if (rgbFrameBuffer == null) {//Log.i("FeedFrameThread", "addRgbFrame3");
                rgbFrameBuffer = rgbFrame;
            }
            makeComplexFrame();
        }
    }

    public static void addIRFrame(CameraPreviewData infraFrame) {
        synchronized (ComplexFrameHelper.class) {
            if (irFrameBuffer == null) {
                irFrameBuffer = infraFrame;
            }
            makeComplexFrame();
        }
    }

    public static Pair<CameraPreviewData, CameraPreviewData> takeComplexFrame() throws InterruptedException {
        return complexFrameQueue.take();
    }


}
