package megvii.testfacepass.custom.httpserver;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import megvii.testfacepass.custom.importmanager.APIUtil;
import megvii.testfacepass.custom.importmanager.LogUtils;

public class HttpServer extends NanoHTTPD {
    private final static String TAG = HttpServer.class.getSimpleName();
    /**
     * logger to log to.
     */
    private static final Logger LOG = Logger.getLogger(HttpServer.class.getName());
    private WorkThread mWorkThread;
    private ArrayBlockingQueue<ImageData> mImageQueue;
    private static int mId;

    public HttpServer() {
//        super(8082);
        super(65000);
        mImageQueue = new ArrayBlockingQueue<ImageData>(20);
        mId = 0;
        mWorkThread = new WorkThread();
    }

    @Override
    public void start() throws IOException {
        super.start();
        //启动线程
        mWorkThread.isInterrupt = false;
        mWorkThread.start();
    }

    @Override
    public void stop() {
        super.stop();
        //停止线程
        mWorkThread.isInterrupt = true;
        mWorkThread.interrupt();
    }

    @Override
    public Response serve(IHTTPSession session) {
//        return testHello(session);
        return process(session);
    }

    private Response process(IHTTPSession session) {
        long time = System.currentTimeMillis();
        Log.i(TAG, "process...");
        Map<String, String> files = new HashMap<String, String>();
        String bodyStr;
        String ret = "{\"msgCode\":-1,\"msg\":\"data error\"}";
        try {
            Log.i(TAG, "-");
            // 这一句话必须要写，否则在获取数据时，获取不到数据
            session.parseBody(files);
            Log.i(TAG, "--");
            bodyStr = files.get("postData");  //获取body的json数据https://blog.csdn.net/wan_ing/article/details/80028894
//            LogUtils.i(TAG, "bodyStr:\n" + bodyStr);
            if (null != bodyStr) {
                Log.i(TAG, "len:" + bodyStr.length());
                ret = parseJsonString(bodyStr);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ResponseException e) {
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder();
//        Method method = session.getMethod();
//        String uri = session.getUri();
//        HttpServer.LOG.info(method + " '" + uri + "' ");
//        LogUtils.i(TAG, "uri: " + uri);//如果有uri,会打印出uri

//        Map<String, String> parms = session.getParms();
//        String data = parms.get("image");//这里的data是POST提交表单时key
//        if (!TextUtils.isEmpty(data)) {
//            LogUtils.i(TAG, "len:" + data.length());
//        }
//        LogUtils.i(TAG, "image:\n" + data);
//        String type = parms.get("image_type");//这里的data是POST提交表单时key
//        if (!TextUtils.isEmpty(type)) {
//            LogUtils.i(TAG, "type: " + type);
//        }
//        if ("base64".equals(type) && !TextUtils.isEmpty(data)) {  //类型合法
//            boolean ret = mImageQueue.offer(new ImageData(++id, type, data, null));  //存入队列
//            LogUtils.e(TAG, "offer:" + ret + ",size:" + mImageQueue.size());
//            builder.append("{\"msgCode\":0,\"msg\":\"success\"}");// 反馈给调用者的数据
//        } else {
//            LogUtils.e(TAG, "image_type or image error");
//            builder.append("{\"msgCode\":-1,\"msg\":\"failed\"}");// 反馈给调用者的数据
//        }

        builder.append(ret);// 反馈给调用者的数据
        Response response = newFixedLengthResponse(builder.toString());
        time = System.currentTimeMillis() - time;
        Log.e(TAG, "==============================time:" + time + "ms");
        return response;
    }

    private Response testHello(IHTTPSession session) {
        LogUtils.i(TAG, "testHello...");
        Method method = session.getMethod();
        String uri = session.getUri();
        HttpServer.LOG.info(method + " '" + uri + "' ");

        String msg = "<html><body><h1>Hello server</h1>\n";
        Map<String, String> parms = session.getParms();
        if (parms.get("username") == null) {
            msg += "<form action='?' method='get'>\n" + "  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n";
        } else {
            msg += "<p>Hello, " + parms.get("username") + "!</p>";
        }

        msg += "</body></html>\n";

        return newFixedLengthResponse(msg);
    }

    private String parseJsonString(String str) {
        String ret = "{\"msgCode\":-2,\"msg\":\"json string error\"}";
        LogUtils.i(TAG, "json parse");
        //{"image_type":"base64","image":"......"}
        JSONObject object = JSONObject.parseObject(str);
        if (null != object) {
            LogUtils.i(TAG, "check image type");
            String type = object.getString("image_type");
            String image = object.getString("image");
            ret = "{\"msgCode\":-3,\"msg\":\"image type or image data error\"}";
            if (type.equals("base64") && !TextUtils.isEmpty(image)) {
                LogUtils.i(TAG, "image len:" + image.length());
                boolean result = mImageQueue.offer(new ImageData(++mId, type, image, null));  //存入队列
                LogUtils.e(TAG, "offer:" + result + ",size:" + mImageQueue.size());
                ret = "{\"msgCode\":0,\"msg\":\"success\"}";
            }
        }
        return ret;
    }

    private class WorkThread extends Thread {
        private final String WorkThreadTAG = WorkThread.class.getSimpleName();
        boolean isInterrupt;

        @Override
        public void run() {
            LogUtils.i(WorkThreadTAG, "run");
            while (!isInterrupt) {
                LogUtils.i(WorkThreadTAG, "run1");
                ImageData data = null;
                try {
                    LogUtils.i(WorkThreadTAG, "mImageQueue.take() enter");
                    data = mImageQueue.take();
                    LogUtils.i(WorkThreadTAG, "mImageQueue.take() leave");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                //decode base64
                try {
                    LogUtils.i(TAG, "before base64 len:" + data.getImage().length());
                    byte[] byteArr = APIUtil.decodeBase64(data.getImage());
                    if ((null != byteArr) && (byteArr.length > 0)) {
                        LogUtils.i(TAG, "after base64 len:" + byteArr.length);
                        data.setByteArr(byteArr);  //将base64 decode后的数据保存
                        EventBus.getDefault().post(data);  //发送消息
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        @Override
        public synchronized void start() {
            super.start();
        }

        @Override
        public void interrupt() {
            LogUtils.e(WorkThreadTAG, "interrupt");
            isInterrupt = true;
            super.interrupt();
        }
    }

}
