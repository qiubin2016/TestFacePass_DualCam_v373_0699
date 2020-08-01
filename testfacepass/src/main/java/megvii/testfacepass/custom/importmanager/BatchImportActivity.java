package megvii.testfacepass.custom.importmanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lee.zbardemo.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassDetectionResult;
import mcv.facepass.types.FacePassExtractFeatureResult;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionResultType;
import megvii.testfacepass.MainActivity;
import megvii.testfacepass.R;
import megvii.testfacepass.camera.CameraPreviewData;


/**
 * 批量导入
 * Created by v_liujialu01 on 2019/5/27.
 */

public class BatchImportActivity extends BaseActivity implements View.OnClickListener, OnImportListener {
    private static final String TAG = BatchImportActivity.class.getSimpleName();
    private final String GROUP_NAME = "facepass";
    private final String DELIMITER = "-";
    private final int WIDTH_MAX = 2560;
    private final int HEIGHT_MAX = 2560;
    private ExecutorService mExecutorService;
    private Future mFuture;
    private Toast toast;

    // view
    private Button mAddFace;
    private Button mRegister;
    private Button mGet;
    private Button mDelete;
    private Button mRename;
    private Button mDeleteFile;
    private Button mDeleteSuccess;
    private Button mDeleteFailed;
    private Button mButtonImport;
    private Button mTest;
    private RelativeLayout mRelativeContent;    // 显示说明的布局
    private RelativeLayout mRelativeImport;     // 显示进度的布局
    private RelativeLayout mRelativeFinish;     // 显示结果的布局

    // import
    private ProgressBar mProgressBar;
    private TextView mTextImportFinish;   // 已处理
    private TextView mTextImportSuccess;  // 成功
    private TextView mTextImportFailure;  // 失败
    private TextView mTextImportTotal;    // 总数

    // finish
    private TextView mTextFinish;           // 已处理
    private TextView mTextFinishSuccess;   // 成功
    private TextView mTextFinishFailure;   // 失败
    private TextView mTextFinishTotal;    // 总数

    private Context mContext;
    private volatile boolean mImporting;

    private int mTotalCount;
    private int mFinishCount;
    private int mSuccessCount;
    private int mFailCount;
    /*DetectResult queue*/
    ArrayBlockingQueue<byte[]> mDetectResultQueue;
    ArrayBlockingQueue<CameraPreviewData> mFeedFrameQueue;
    ArrayBlockingQueue<Boolean> mRecognizeQueue;  //识别成功通知队列
    RecognizeThread1 mRecognizeThread1;
    FeedFrameThread1 mFeedFrameThread1;
    private long mStartTime = 0;
    private boolean mTimeLock = false;
    private final int mCamerarotation = 0;  //照片都是0度，无旋转
    private int mCamerarotationBak = 0;  //保存原来Config.rotation
    /* SDK 实例对象 */
    private FacePassHandler mFacePassHandler;
    private boolean isLocalGroupExist = false;
    private ArrayList<Boolean> mFeedFrameResult;
//    private ArrayList<Boolean> mRecognizeResult;
    private boolean mFeedFrameFinish;
    private int mFeedFrameTotalCount, mFeedFrameSuccessCount, mRecognizeTotalCount;

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_import);
        mContext = this;
        initView();
        initData();
    }

    @Override
    protected void onResume() {
        checkGroup();  //检查底库是否存在
        startRecognize();
        super.onResume();
    }

    @Override
    protected void onPause() {
        stopRecognize();
        mDetectResultQueue.clear();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放
        ImportFileManager.getInstance().release();
        release();
    }

    private void initView() {
        Button buttonBack = (Button) findViewById(R.id.button_import_back);
        buttonBack.setOnClickListener(this);
        mAddFace = (Button) findViewById(R.id.button_addface);
        mAddFace.setOnClickListener(this);
        mRegister = (Button) findViewById(R.id.button_register);
        mRegister.setOnClickListener(this);
        mGet = (Button) findViewById(R.id.button_get);
        mGet.setOnClickListener(this);
        mDelete = (Button) findViewById(R.id.button_delete);
        mDelete.setOnClickListener(this);
        mRename = (Button) findViewById(R.id.button_rename);
        mRename.setOnClickListener(this);
        mDeleteFile = (Button) findViewById(R.id.button_delete_file);
        mDeleteFile.setOnClickListener(this);
        mDeleteSuccess = (Button) findViewById(R.id.button_delete_success);
        mDeleteSuccess.setOnClickListener(this);
        mDeleteFailed = (Button) findViewById(R.id.button_delete_failed);
        mDeleteFailed.setOnClickListener(this);
        mButtonImport = (Button) findViewById(R.id.button_import);
        mButtonImport.setOnClickListener(this);
        mTest = (Button) findViewById(R.id.button_test);
        mTest.setOnClickListener(this);
        mRelativeContent = (RelativeLayout) findViewById(R.id.relative_content);
        mRelativeImport = (RelativeLayout) findViewById(R.id.relative_progress);
        mRelativeFinish = (RelativeLayout) findViewById(R.id.relative_finish);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mTextImportFinish = (TextView) findViewById(R.id.text_import_finish);
        mTextImportSuccess = (TextView) findViewById(R.id.text_import_success);
        mTextImportFailure = (TextView) findViewById(R.id.text_import_failure);
        mTextImportTotal = (TextView) findViewById(R.id.text_import_total);
        mTextFinish = (TextView) findViewById(R.id.text_finish);
        mTextFinishSuccess = (TextView) findViewById(R.id.text_finish_success);
        mTextFinishFailure = (TextView) findViewById(R.id.text_finish_failure);
        mTextFinishTotal = (TextView) findViewById(R.id.text_finish_total);
    }

    private void initData() {
        ImportFileManager.getInstance().setOnImportListener(this);
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        if (null == mFacePassHandler)
            Log.e(TAG, "mFacePassHandler is null");
        else
            Log.e(TAG, "mFacePassHandler is not null");
        if (null == MainActivity.mFacePassHandler)
            Log.e(TAG, "MainActivity.mFacePassHandler is null");
        else
            Log.e(TAG, "MainActivity.mFacePassHandler is not null");
        mFacePassHandler = MainActivity.mFacePassHandler;
        if (null == mFeedFrameThread1) {
            Log.e(TAG, "mFeedFrameThread is null");
        }
        mDetectResultQueue = new ArrayBlockingQueue<byte[]>(5);
        mFeedFrameQueue = new ArrayBlockingQueue<CameraPreviewData>(1);
        mRecognizeQueue = new ArrayBlockingQueue<Boolean>(1);
        mFeedFrameResult = new ArrayList<Boolean>(2);
//        mRecognizeResult = new ArrayList<Boolean>(2);
        mFeedFrameThread1 = new FeedFrameThread1();
        mRecognizeThread1 = new RecognizeThread1();
    }

    @Override
    public void onClick(View view) {
        Log.i(TAG, "===============================onclicked!" + view.getId());
        switch (view.getId()) {
            case R.id.button_import:   // 点击导入数据按钮
                if (!mImporting) {
                    // 如果按钮文案是“搜索SD卡，并导入数据”，则执行批量导入操作
                    if ("搜索SD卡，并导入数据".equals(mButtonImport.getText().toString())) {
                        mImporting = true;
                        ToastUtils.toast(mContext, "搜索中，请稍后");
                        ImportFileManager.getInstance().batchImport();
                        // 如果按钮文案是“确认”，则说明导入结束，返回上一层一面
                    } else if ("确认".equals(mButtonImport.getText().toString())) {
                        finish();
                    } else {
                        Log.i("BatchImportActivity", "");
                    }
                }
                break;
            case R.id.button_addface:  //入库
                addFace();
                break;
            case R.id.button_register: //向后台注册用户
                register();
//                testBtton();
                break;
            case R.id.button_get:
                get();
                break;
            case R.id.button_delete:
                delete();
                break;
            case R.id.button_rename:
                rename();
                break;
            case R.id.button_delete_file:
                deleteFile();
                break;
            case R.id.button_delete_success:
                deleteSuccess();
                break;
            case R.id.button_delete_failed:
                deleteFailed();
                break;
            case R.id.button_test:
                test();
                break;
            case R.id.button_import_back:
                // 释放
                ImportFileManager.getInstance().release();
                release();
                finish();
                break;

            default:
                break;
        }
    }

    /**
     * 开始解压
     */
    @Override
    public void startUnzip() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mButtonImport == null) {
                    return;
                }
                ToastUtils.toast(mContext, "搜索到压缩包，开始解压");
                mButtonImport.setText("解压中......");
                mButtonImport.setBackgroundColor(Color.parseColor("#7f7f7f"));
            }
        });
    }

    /**
     * 解压完毕，显示导入进度View
     */
    @Override
    public void showProgressView() {
        //初始化计数
        mTotalCount = 0;
        mFinishCount = 0;
        mSuccessCount = 0;
        mFailCount = 0;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRelativeContent == null || mRelativeImport == null || mRelativeFinish == null) {
                    return;
                }
                mRelativeContent.setVisibility(View.GONE);
                mRelativeImport.setVisibility(View.VISIBLE);
                mRelativeFinish.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 正在导入，实时更新导入状态
     */
    @Override
    public void onImporting(final int finishCount, final int successCount, final int failureCount,
                            final float progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressBar == null || mTextImportFinish == null
                        || mTextImportSuccess == null || mTextImportFailure == null
                        || mButtonImport == null) {
                    return;
                }
                mButtonImport.setText("导入中......请勿退出此页面");
                mButtonImport.setBackgroundColor(Color.parseColor("#7f7f7f"));
                mProgressBar.setProgress((int) (progress * 100));
                mTextImportFinish.setText("已处理：" + finishCount);
                mTextImportSuccess.setText("导入成功：" + successCount);
                mTextImportFailure.setText("导入失败：" + failureCount);
            }
        });
    }

    @Override
    public void onImporting(final int finishCount, final int successCount, final int failureCount,
                            final int totalCount, final float progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressBar == null || mTextImportFinish == null
                        || mTextImportSuccess == null || mTextImportFailure == null
                        || mButtonImport == null) {
                    return;
                }
                mButtonImport.setText("导入中......请勿退出此页面");
                mButtonImport.setBackgroundColor(Color.parseColor("#7f7f7f"));
                mProgressBar.setProgress((int) (progress * 100));
                mTextImportFinish.setText("已处理：" + finishCount);
                mTextImportSuccess.setText("导入成功：" + successCount);
                mTextImportFailure.setText("导入失败：" + failureCount);
                mTextImportTotal.setText("总数：" + totalCount);
            }
        });
    }

    private void processSuccess() {
        // 更新进度
        onImporting(++mFinishCount, ++mSuccessCount, mFailCount, mTotalCount, ((float) mFinishCount / (float) mTotalCount));
    }

    private void processFailed() {
        // 更新进度
        onImporting(++mFinishCount, mSuccessCount, ++mFailCount, mTotalCount, ((float) mFinishCount / (float) mTotalCount));
    }

    /**
     * 导入结束，显示导入结果
     */
    @Override
    public void endImport(final int finishCount, final int successCount, final int failureCount) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRelativeContent == null || mRelativeImport == null || mRelativeFinish == null) {
                    return;
                }
                mRelativeContent.setVisibility(View.GONE);
                mRelativeImport.setVisibility(View.GONE);
                mRelativeFinish.setVisibility(View.VISIBLE);
                mTextFinish.setText("已处理：" + finishCount);
                mTextFinishSuccess.setText("导入成功：" + successCount);
                mTextFinishFailure.setText("导入失败：" + failureCount);
                mButtonImport.setText("确认");
                mButtonImport.setBackgroundColor(Color.parseColor("#036838"));
                mImporting = false;
            }
        });
    }

    public void endImport(final int finishCount, final int successCount, final int failureCount, final int totalCount) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRelativeContent == null || mRelativeImport == null || mRelativeFinish == null) {
                    return;
                }
                mRelativeContent.setVisibility(View.GONE);
                mRelativeImport.setVisibility(View.GONE);
                mRelativeFinish.setVisibility(View.VISIBLE);
                mTextFinish.setText("已处理：" + finishCount);
                mTextFinishSuccess.setText("导入成功：" + successCount);
                mTextFinishFailure.setText("导入失败：" + failureCount);
                mTextFinishTotal.setText("总数：" + totalCount);
                mButtonImport.setText("确认");
                mButtonImport.setBackgroundColor(Color.parseColor("#036838"));
                mImporting = false;
            }
        });
    }

    /**
     * 提示导入过程中的错误信息
     */
    @Override
    public void showToastMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (message == null) {
                    return;
                }
                ToastUtils.toast(mContext, message);
                mImporting = false;
            }
        });
    }

    private void addFace() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (isFacePassHandlerNull()) {
                    return;
                }
                Log.i(TAG, "===============================addFace");
                checkGroup();  //检查底库是否存在
                if (!isLocalGroupExist) {  //group不存在，创建
                    boolean isSuccess = false;
                    try {
                        isSuccess = mFacePassHandler.createLocalGroup(GROUP_NAME);
                    } catch (FacePassException e) {
                        e.printStackTrace();
                    }
                    toast("create group " + isSuccess);
                    if (isSuccess) {
                        isLocalGroupExist = true;
                    }
                }
                if (isLocalGroupExist) {
                    // 遍历目录是不是存在文件
                    File batchPicDir = FileUtils.getBatchImportDirectory();
                    LogUtils.i(TAG, "dir:" + batchPicDir);
                    File[] files = batchPicDir.listFiles();
                    // 如果该目录下没有文件，则提示获取图片失败
                    if (files == null) {
                        toast("图片不存在 ！");
                        return;
                    }
                    // 如果该目录下有文件，则判断该文件是目录还是文件
                    File[] picFiles;   // 定义图片文件数组

                    if (files[0].isDirectory()) {
                        picFiles = files[0].listFiles();
                    } else {
                        picFiles = files;
                    }
                    //遍历保存入库成功图片的目录
                    File successDir = FileUtils.getBatchImportSuccessDirectory();
                    File[] successFiles = successDir.listFiles();
                    //遍历保存入库失败图片的目录
                    File failedDir = FileUtils.getBatchImportSuccessDirectory();
                    File[] failedFiles = successDir.listFiles();
                    // 开始显示进度条
                    showProgressView();
                    mTotalCount = picFiles.length;
                    //调整rotation
                    if (!modifyRotation()) {
                        Log.e(TAG, "setConfig failed");
                        return;
                    }
                    for (int i = 0; i < picFiles.length; i++) {
                        Log.i(TAG, "i:" + i);
                        // 获取图片名
                        String picName = picFiles[i].getName();
                        // 判断图片后缀
                        if (!picName.endsWith(".jpg") && !picName.endsWith(".png")) {
                            processFailed();  //更新进度
                            LogUtils.i(TAG, "图片后缀不满足要求");
                            continue;
                        }
                        //判断文件名是否符合规则 文件名中是否有"facepass-"
                        if (-1 != picName.indexOf(DELIMITER + GROUP_NAME)) {
                            File imageFile = picFiles[i];
                            if (!imageFile.exists()) {
                                processFailed();  //更新进度
                                toast("图片不存在！path:" + imageFile.getPath() + ",name:" + imageFile.getName());
                                continue;
                            }
                            String imagePath = imageFile.getPath();
                            Log.i(TAG, "imagePath:" + imagePath);
                            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                            if (null != bitmap) {
                                Log.i(TAG, "config:" + bitmap.getConfig().toString());
                                boolean isProcessed = false;  //图片是否已经被处理
                                for (File file : successFiles) {
                                    if (imageFile.getName().equals(file.getName())) {
                                        isProcessed = true;  //保存成功图片目录中找到相同图片
                                        Log.i(TAG, "保存成功图片目录中找到相同图片");
                                        break;
                                    }
                                }
                                if (!isProcessed) {
                                    for (File file : failedFiles) {
                                        if (imageFile.getName().equals(file.getName())) {
                                            isProcessed = true;  //保存失败图片目录中找到相同图片
                                            Log.i(TAG, "保存失败图片目录中找到相同图片");
                                            break;
                                        }
                                    }
                                }
                                if (isProcessed) {  //确认已被处理，更新进度
                                    processSuccess();  //更新进度
//                                imageFile.delete();  //删除图片
                                    Log.e(TAG, "删除图片1：" + imageFile.getName());
//                                continue;  /*=====================success=====================*/
                                }
                                final int widthPic = bitmap.getWidth();
                                final int heightPic = bitmap.getHeight();
                                Log.i(TAG, "width:" + widthPic + ",height:" + heightPic);
                                if ((widthPic <= WIDTH_MAX) && (heightPic <= HEIGHT_MAX)) {  //检查照片分辨率
                                    try {
                                        boolean isExist = isFaceExist(mFacePassHandler, bitmap, widthPic, heightPic);  //图片是否有人脸，是否在人脸库中
                                        if (isExist) {  //人脸重复
                                            processSuccess();  //更新进度
//                                            imageFile.delete();  //删除图片
                                            Log.e(TAG, "重复人脸！删除图片2：" + imageFile.getName());
                                            continue;  /*=====================success=====================*/
                                        }
                                        android.util.Log.d("addface", "addFace");
                                        FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);  //人脸图片入库
                                        android.util.Log.d("addface", "result:" + result
                                                + ",bl:" + result.blur
                                                + ",pp:" + result.pose.pitch
                                                + ",pr:" + result.pose.roll
                                                + ",py" + result.pose.yaw);
                                        Log.e("addface", "ret:" + result.result + ",blur:" + result.blur + ",brightness:" + result.brightness + ",deviation:" + result.deviation + ",pitch:" + result.pose.pitch
                                                + ",roll:" + result.pose.roll + ",yaw:" + result.pose.yaw + ",left:" + result.facePassRect.left + ",right:" + result.facePassRect.right
                                                + ",top:" + result.facePassRect.top + ",bottom:" + result.facePassRect.bottom);
                                        int width = result.facePassRect.right - result.facePassRect.left;
                                        int height = result.facePassRect.bottom - result.facePassRect.top;
                                        Log.e("addface", "face:" + width + "x" + height);
                                        boolean isSuccess = false;
                                        if (result != null) {
                                            Log.i(TAG, "result is not null");
                                            if (result.result == 0) {  //入库成功
                                                Log.e(TAG, "入库成功");
                                                String faceToken = new String(result.faceToken);
                                                String bindStr = "";
                                                try {
                                                    isSuccess = mFacePassHandler.bindGroup(GROUP_NAME, result.faceToken);
                                                    bindStr = isSuccess ? "success" : "failed";
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    toast(e.getMessage());
                                                }
                                                Log.i(TAG, "add face successfully! facetoken:" + result.faceToken + ",bind:" + bindStr);
                                                if (isSuccess) {  //绑定底库成功
                                                    Log.i(TAG, "bindGroup success");
                                                    // 保存图片到成功目录中
                                                    saveBitmaptoDirectory(FileUtils.getBatchImportSuccessDirectory(), picName, bitmap);
                                                    processSuccess();  //更新进度
                                                    continue;  /*=====================success=====================*/
                                                }
                                            } else if (result.result == 1) {
                                                Log.e(TAG, "no face!");
                                            } else {
                                                Log.e(TAG, "quality problem！");
                                            }
                                            Log.i(TAG, "---------2");
                                        }
                                    } catch (FacePassException e) {
                                        e.printStackTrace();
                                        toast(e.getMessage());
                                    }
                                }
                                // 保存图片到失败目录中
                                saveBitmaptoDirectory(FileUtils.getBatchImportFailedDirectory(), picName, bitmap);
                                // 图片回收
                                if (!bitmap.isRecycled()) {
                                    bitmap.recycle();
                                }
//                            imageFile.delete();  //删除图片
                                Log.e(TAG, "删除图片：" + imageFile.getName());
                            }
                            processFailed();  //更新进度
                        }
                    }
                    //恢复rotation
                    if (!resumeRotation()) {
                        Log.e("addface", "setAddFaceConfig1 failed");
                    }
                }
                Log.i(TAG, "addFace finish！");
                // 导入完成
                endImport(mFinishCount, mSuccessCount, mFailCount, mTotalCount);
            }
        });
    }

    private boolean isFaceExist(FacePassHandler facePassHandler, Bitmap bitmap, final int width, final int height) {
        boolean ret = false;
        Log.i("addface", "bitmapToNv21");
        byte[] bytes = ImageUtils.bitmapToNv21(bitmap, width, height);
//                                    byte[] bytes = ImageUtils.bitmap2RGB(bitmap);
//                                    byte[] bytes = Bitmap2Bytes(bitmap);
//                                    byte[] bytes = ImageUtils.getNV21(widthPic, heightPic, bitmap);
        Log.i("addface", "bitmap2RGB:" + bytes.length + ",width:" + width + ",height:" + height);
        startFeedFrameRecognize();  //初始化检测和识别相关的数据
        //两帧
        addRgbFrame(bytes.clone(), width, height);
        Log.i(TAG, "addRgbFrame");
        addRgbFrame(bytes.clone(), width, height);
        Log.i(TAG, "addRgbFrame");
        try {
            Log.i(TAG, "mRecognizeQueue.take() enter");
            ret = mRecognizeQueue.take();
            Log.i(TAG, "mRecognizeQueue.take() leave");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        FacePassImage image = null;
//        try {
//            //这里rotation传什么都无所谓，setAddFaceConfig()已经设置好
//            image = new FacePassImage(bytes, width, height, 0, FacePassImageType.NV21);
//            /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
//            android.util.Log.d("addface", "feedFrame");
//            FacePassDetectionResult detectionResult = facePassHandler.feedFrame(image);  //人脸检测
//            Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);
//            if (detectionResult == null || detectionResult.faceList.length == 0) {  //人脸检测失败，再尝试一次
//                Log.d(TAG, "feedFrame failed!");
//                detectionResult = facePassHandler.feedFrame(image);  //人脸检测
//                Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);
//            }
//            if (detectionResult != null || detectionResult.faceList.length > 0)  //人脸检测成功
//            {
//                Log.d(TAG, "feedFrame success!");
//                Log.e("addface", "有人脸 len:" + detectionResult.message.length);
//                FacePassRecognitionResult[] recognizeResult = facePassHandler.recognize(GROUP_NAME, detectionResult.message);  //识别
//                Log.e("addface", "recognize");
//                if (recognizeResult != null && recognizeResult.length > 0) {  //识别成功
//                    Log.e("addface", "人脸识别有结果");
//                    for (FacePassRecognitionResult result : recognizeResult) {
//                        if (FacePassRecognitionResultType.RECOG_OK == result.facePassRecognitionResultType) {
//                            ret = true;
//                            Log.d("addface", "识别成功");
//                        }
//                        showRecognizeReslt(result);  //打印识别结果
//                    }
//                } else {
//                    Log.e("addface", "人脸识别失败");
//                }
//            } else {
//                Log.e("addface", "无人脸");
//            }
//        } catch (FacePassException e) {
//            e.printStackTrace();
//            Log.i("addface", "FacePassException");
//        }
        return ret;
    }

    private void delete() {
        if (isFacePassHandlerNull()) {
            return;
        }
        boolean isSuccess = false;
        try {
            isSuccess = mFacePassHandler.deleteLocalGroup(GROUP_NAME);
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "delete:" + isSuccess);
        toast("delete:" + isSuccess);
    }

    private void get() {
        if (isFacePassHandlerNull()) {
            return;
        }
        int num = 0;
        try {
            num = mFacePassHandler.getLocalGroupFaceNum(GROUP_NAME);
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "get:" + num);
        toast("get:" + num);
    }

    private void rename() {  //图片重命名
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //遍历该目录是不是存在文件
                File batchPicDir = FileUtils.getBatchImportDirectory();
                LogUtils.i(TAG, "dir:" + batchPicDir);
                File[] files = batchPicDir.listFiles();

                // 如果该目录下没有文件，则提示获取图片失败
                if (files == null) {
                    toast("获取图片失败");
                    return;
                }
                // 如果该目录下有文件，则判断该文件是目录还是文件
                File[] picFiles;   // 定义图片文件数组

                if (files[0].isDirectory()) {
                    picFiles = files[0].listFiles();
                } else {
                    picFiles = files;
                }

                // 开始显示进度条
                showProgressView();
                mTotalCount = picFiles.length;

                int renameCount = 0;  //重命名文件的计数器
                for (int i = 0; i < picFiles.length; i++) {
                    // 获取图片名
                    String picName = picFiles[i].getName();
                    // 判断图片后缀
                    if (!picName.endsWith(".jpg") && !picName.endsWith(".png")) {
                        LogUtils.i(TAG, "图片后缀不满足要求");
                        processFailed();  //更新进度
                        continue;
                    }
                    //判断文件名是否符合规则 文件名中是否有"-facepass"
                    if (-1 == picName.indexOf(DELIMITER + GROUP_NAME)) {
                        //更改文件名
                        String oldPath = FileUtils.getPathFromFilepath(picFiles[i].getPath());  //获取当前绝对路径
                        String fileSuffix = FileUtils.getSuffix(picName);  //获取图片后缀，".jpg"/".png"
                        Log.i(TAG, "filename:" + picName + ",file path:" + picFiles[i].getPath() + "oldPath:" + oldPath);
                        Log.i(TAG, "suffix:" + FileUtils.getSuffix(picName));
                        //新文件名：group name + yyyyMMddHHmmss + 00001 + 图片后缀；例如：2020071509412500001-facepass.jpg
                        String newFileName = FileUtils.getTimestampName() + String.format("%05d", renameCount) + DELIMITER + GROUP_NAME + fileSuffix;
                        String newPath = FileUtils.makePath(oldPath, newFileName);  //拼接当前绝对路径 + 文件名
                        Log.i(TAG, "new filename:" + newFileName + ",path:" + newPath);
                        boolean ret1 = picFiles[i].renameTo(new File(newPath));
                        picName = picFiles[i].getName();
                        Log.i(TAG, "filename:" + picName + ",ret:" + ret1);
                        if (ret1) {  //重命名成功
                            renameCount++;
                        } else {
                            LogUtils.i(TAG, "图片重命名失败");
                            processFailed();  //更新进度
                            continue;
                        }
                    }
                    processSuccess();  //更新进度
                }
                // 导入完成
                endImport(mFinishCount, mSuccessCount, mFailCount, mTotalCount);
            }
        });

    }

    private void deleteFile() {
        deleteFiles(FileUtils.getBatchImportDirectory());
    }

    private void deleteSuccess() {
        deleteFiles(FileUtils.getBatchImportSuccessDirectory());
    }

    private void deleteFailed() {
        deleteFiles(FileUtils.getBatchImportFailedDirectory());
    }

    private void deleteFiles(File file) {
        //遍历该目录是不是存在文件
        File batchPicDir = file;
        LogUtils.i(TAG, "dir:" + batchPicDir);
        File[] files = batchPicDir.listFiles();

        // 如果该目录下有文件，则删除
        if (files != null) {
            for (File file1 : files) {
                file1.delete();
            }
        }
        Log.i(TAG, "delete complete");
    }

    private void testBtton() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (isFacePassHandlerNull()) {
                    return;
                }
                int num = 0;
                try {
                    num = mFacePassHandler.getLocalGroupFaceNum(GROUP_NAME);
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "get1:" + num);
//                toast("get:" + num);
                Log.i(TAG, "test");
                test1();
                Log.i(TAG, "jpgFeedFrame");
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg", 640, 480, 0);
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg", 640, 480, 90);
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg", 640, 480, 180);
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg", 640, 480, 270);

                if (!jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg", 720, 1280, 90)) {
                    jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg", 720, 1280, 90);
                }
                jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg", 720, 1280, 180);
                jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg", 720, 1280, 270);
                jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg", 720, 1280, 0);
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg", 640, 640, 0);
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg", 640, 640, 90);
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg", 640, 640, 180);
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg", 640, 640, 270);
                Log.i(TAG, "test finish!");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 10; i++) {
                            Bitmap bitmap = BitmapFactory.decodeFile("/storage/emulated/0/Face-Import/1111-facepass.jpg");
                            isFaceExist(mFacePassHandler, bitmap, 720, 1280);
                        }
                    }
                }).start();

            }
        });
    }

    private void test() {
        if (isFacePassHandlerNull()) {
            return ;
        }
        String str = "6a 33 92 75 7e 73 7d 87 80 9d 6b 78 8b 7b 87 71 6d 74 82 67 6d 51 75 8d 8d 81 8f 60 86 73 80 61 8a 33 87 94 66 91 8a 98 ab 69 65 af 88 70 7e 87 90 8f 62 6b 7a 92 6f 5a 83 74 51 7a 8c 7f 9a 85 90 4f 76 8a 81 7e 66 84 84 6d 82 83 ae ac 5b 77 7b 85 7e 70 93 83 96 6a b2 77 76 53 66 84 8e 75 6c aa 80 6d 8c 9d 70 90 69 92 74 c0 79 5e 83 8c 8d 8c 3e 73 55 6f 8f bb 60 6f 6b 7b 89 6a 86 94 49 af 99 74 6b 62 7b 6e 66 a2 71 a8 8f 57 83 86 9d 5e 98 73 8f 6c 9b 56 68 4d 84 75 56 95 5e 84 88 96 9f 76 77 7e 88 86 6a 68 81 54 57 87 a8 ac 98 89 72 74 8b 69 9e b1 68 40 90 85 98 73 7e 94 96 80 7e 61 79 67 84 9b 91 a7 65 88 a0 73 95 4b 95 80 89 9d a9 7d 75 a1 85 a9 85 9f 8b a4 7b 8f 97 96 84 92 75 82 74 5f 7e 59 a6 53 70 7d 93 7c 7c 59 79 bc 84 8e a9 8d 83 72 a4 7d 91 5c 39 9b 98 82 82 8d 71 7f 86 80 92 89 66 67 7c 79 7d 73 6c 89 54 91 94 59 80 8b 5a 6b 69 4f 6d 6a af 82 77 92 85 79 bb 52 77 82 5d ab 93 76 7e 7f 7d 70 6a 60 72 66 91 7b 8c b1 81 66 8f a1 9b 8c ae 52 8f 90 84 5d 9f 60 9a 86 8a 77 6d 81 a6 6f 8e 80 89 8f 9a 6f 88 6b 64 73 9b 6a 87 63 8c 73 93 8e 60 6e 92 77 a0 86 84 6b 79 8b 6b ab 84 5a 7d 82 5b 72 6e 67 9a 64 8e 8d 8f 85 a0 91 68 93 70 7b 5b 81 78 7e 9a 93 74 4d b4 5a 86 93 8b 92 7a 72 6b 7d 79 7d 5e b3 53 8d 95 85 89 87 6a a3 72 9e 89 73 64 9c 5a 74 63 b1 8d 86 55 b1 77 95 81 96 79 5c 84 73 73 87 6a 56 85 68 56 63 72 99 91 8e 58 45 54 83 a9 8e ac 63 af 87 60 7c 9c 37 4c 93 93 73 8a 99 83 96 8c 81 77 a2 81 96 82 8f 79 7f 8e 9d 98 83 6a 99 65 83 90 93 63 74 80 9c 70 3a 8f 81 70 7e 69 89 6c 7c 77 68 a0 3f 6f 7a 7f 82 8f 13 02 4b";
        str = str.replace(" ", "");  //将所有空格删除
        Log.i(TAG, "str:\n" + str);
        byte[] bytes = new byte[512];
        String faceToken;
        try {
            faceToken = mFacePassHandler.insertFeature(bytes, null);
            Log.i(TAG, "faceToken:" + faceToken);
        } catch (FacePassException e) {
            e.printStackTrace();
        }

        //提取特征值
        FacePassExtractFeatureResult result;
        boolean isSuccess;
        String faceTokenStr;
        Bitmap bitmap = BitmapFactory.decodeFile("/storage/emulated/0/DCIM/Camera/qiub4.jpg");
        try {
            result = mFacePassHandler.extractFeature(bitmap);
            Log.i(TAG, "ret:" + result.result + ",len:" + result.featureData.length + ",data:\n" + Util.Companion.ByteArray2HexStr(result.featureData, true));
            faceToken = mFacePassHandler.insertFeature(result.featureData, null);
            faceTokenStr = new String(faceToken);
            Log.i(TAG, "faceToken:" + faceTokenStr);
            isSuccess = mFacePassHandler.bindGroup(GROUP_NAME, faceTokenStr.getBytes());
            Log.i(TAG, "bindGroup:" + (isSuccess ? "success" : "failed"));
        } catch (FacePassException e) {
            e.printStackTrace();
        }
    }

    private boolean test1() {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        FacePassImage image;
        try {
            byte[] bytes = ImageUtils.readYuvFileToByteArray("/storage/emulated/0/MyImage/20200717090502-00000000.yuv");
            Log.i("FeedFrameThread", "len:" + bytes.length);
            image = new FacePassImage(bytes, 640, 480, 90, FacePassImageType.NV21);
        } catch (FacePassException e) {
            e.printStackTrace();
            return ret;
        }

        /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
        FacePassDetectionResult detectionResult = null;
        try {

            detectionResult = mFacePassHandler.feedFrame(image);
        } catch (FacePassException e) {
            e.printStackTrace();
            return ret;
        }

        if (detectionResult == null || detectionResult.faceList.length == 0) {
            Log.d(TAG, "feedFrame failed!");
        } else {
            Log.d(TAG, "feedFrame success!");
            ret = true;
        }
        return ret;
    }

//blur:0.20032844,brightness:68.83499,deviation:28.312532,pitch:-2.4827423,roll:6.9652085,yaw:0.15141112,left:230,right:464,top:285,bottom:519
    private boolean jpgFeedFrame(String path, final int width, final int height, final int rotation) {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        Log.i(TAG, "path:" + path + ",rotation:" + rotation);
        FacePassImage image;
        try {
            byte[] bytes = ImageUtils.readJpgFileToByteArray(path, width, height);
            Log.i("FeedFrameThread", "len:" + bytes.length);
            image = new FacePassImage(bytes, width, height, rotation, FacePassImageType.NV21);
        } catch (FacePassException e) {
            e.printStackTrace();
            return ret;
        }

        /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
        FacePassDetectionResult detectionResult = null;
        try {
            detectionResult = mFacePassHandler.feedFrame(image);
        } catch (FacePassException e) {
            e.printStackTrace();
            return ret;
        }

        if (detectionResult == null || detectionResult.faceList.length == 0) {
            Log.d(TAG, "feedFrame failed!");
        } else {
            Log.d(TAG, "feedFrame success!");
            ret = true;
        }
        return ret;
    }

    private void toast(String msg) {
//        Toast.makeText(BatchImportActivity.this, msg, Toast.LENGTH_SHORT).show();
        //优化Toast显示
        Context context = BatchImportActivity.this;
        int duration = Toast.LENGTH_SHORT;
        if (toast == null) {
            toast = Toast.makeText(context, msg, duration);
        } else {
            toast.setText(msg);
            toast.setDuration(duration);
        }
        toast.show();
    }

    private void release() {
        if (mFuture != null && !mFuture.isCancelled()) {
            mFuture.cancel(true);
            mFuture = null;
        }

        if (mExecutorService != null) {
            mExecutorService = null;
        }
    }

    private boolean saveBitmaptoDirectory(File file, String picName, Bitmap bitmap) {
        boolean ret = false;

        // 保存图片到新目录中
        if (file != null) {
            Log.i(TAG, "facePicDir is not null");
            File savePicPath = new File(file, picName);
            if (FileUtils.saveBitmap(savePicPath, bitmap)) {
                ret = true;
            }
        }
        return ret;
    }

    //调整rotation
    private boolean modifyRotation() {
        if (isFacePassHandlerNull()) {
            return false;
        }
        FacePassConfig config = mFacePassHandler.getConfig();
        mCamerarotationBak = config.rotation;
        config.rotation = 0;
        config.livenessEnabled = false;            //关闭活体 红外活体开关与活体开关，是两个独立的功能，同时打开，SDK只会使用一种活体算法，优先使用红外活体检测
        config.rgbIrLivenessEnabled = false;       //关闭红外活体功能
        try {
            mFacePassHandler.setConfig(config);
        } catch (FacePassException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    //恢复rotation
    private boolean resumeRotation() {
        if (isFacePassHandlerNull()) {
            return false;
        }
        FacePassConfig config = mFacePassHandler.getAddFaceConfig();
        config.rotation = mCamerarotationBak;
        config.livenessEnabled = false;            //关闭活体 红外活体开关与活体开关，是两个独立的功能，同时打开，SDK只会使用一种活体算法，优先使用红外活体检测
        config.rgbIrLivenessEnabled = true;        //打开红外活体功能
        try {
            mFacePassHandler.setConfig(config);
        } catch (FacePassException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean isFacePassHandlerNull() {
        if (null == mFacePassHandler) {
            mFacePassHandler = MainActivity.mFacePassHandler;
            if (null == mFacePassHandler) {
                Log.e(TAG, "FacePassHandle is null ! ");
                return true;
            }
        }
        return false;
    }

    private void startRecognize() {
        mFeedFrameThread1.isInterrupt = false;
        mFeedFrameThread1.start();
        mRecognizeThread1.isInterrupt = false;
        mRecognizeThread1.start();
    }

    private void stopRecognize() {
        mFeedFrameThread1.isInterrupt = true;
        mFeedFrameThread1.interrupt();
        mRecognizeThread1.isInterrupt = true;
        mRecognizeThread1.interrupt();
    }

    private void checkGroup() {  //检查底库是否存在
        if (isFacePassHandlerNull()) {
            return;
        }
        String[] groups = mFacePassHandler.getLocalGroups();  //查询全部底库group
        if (groups != null && groups.length > 0) {
            for (String group : groups) {
                if (GROUP_NAME.equals(group)) {  //找到group
                    isLocalGroupExist = true;
                    break;
                }
            }
        } else {
            Log.e(TAG, "groups is null !");
        }
    }
    private void register() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "submit1");
                UserManager.getBuildList();  //获取楼栋id
                UserManager.getUnitList();  //获取单元id
                UserManager.getRoomList();  //获取房间id
                UserManager.addUserBindRoom();  //添加用户并绑定房间关系
                Log.i(TAG, "submit2");
            }
        });
    }

    private void addRgbFrame(byte[] data, final int width, final int height) {
        Log.i(TAG, "addRgbFrame1");
        mFeedFrameQueue.offer(new CameraPreviewData(data.clone(), width, height,
                0, false));
    }

    private class FeedFrameThread1 extends Thread {
        private final String FeedFrameThreadTAG = FeedFrameThread1.class.getSimpleName();
        boolean isInterrupt;

        @Override
        public void run() {
            Log.i(FeedFrameThreadTAG, "run");
            while (!isInterrupt) {
                Log.i(FeedFrameThreadTAG, "run1");
                CameraPreviewData cameraPreviewData = null;
                try {
                    Log.i(FeedFrameThreadTAG, "takeComplexFrame enter");
                    cameraPreviewData = mFeedFrameQueue.take();
                    Log.i(FeedFrameThreadTAG, "takeComplexFrame leave");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (isFacePassHandlerNull()) {
                    return;
                }
                /* 将相机预览帧转成SDK算法所需帧的格式 FacePassImage */
                long startTime = System.currentTimeMillis(); //起始时间
                if (!mTimeLock) {
                    mStartTime = startTime;
                }
                FacePassImage image;
                try {
                    Log.i(FeedFrameThreadTAG, "FacePassImage");
                    image = new FacePassImage(cameraPreviewData.nv21Data, cameraPreviewData.width, cameraPreviewData.height, mCamerarotation, FacePassImageType.NV21);
                } catch (FacePassException e) {
                    e.printStackTrace();
                    continue;
                }
                /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
                FacePassDetectionResult detectionResult = null;
                try {
                    Log.i(FeedFrameThreadTAG, "feedFrame");
                    detectionResult = mFacePassHandler.feedFrame(image);
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                if (detectionResult == null || detectionResult.faceList.length == 0) {
                    Log.e(FeedFrameThreadTAG, "无人脸");  /* 当前帧没有检出人脸 */

                } else {
                    Log.e(FeedFrameThreadTAG, "有人脸" + ",trackId:" + detectionResult.faceList[0].trackId);
                    if (detectionResult.message.length != 0) {
                        Log.d(FeedFrameThreadTAG, "送识别");
                        mFeedFrameSuccessCount++;
                        Log.d(FeedFrameThreadTAG, "mDetectResultQueue.offer");
                        mDetectResultQueue.offer(detectionResult.message);  //添加到识别队列
                        mTimeLock = true;  //锁定mStartTime，直到recognize完成
                        Log.i("]time", "mDetectResultQueue.offer");
                    }
                }
                mFeedFrameTotalCount++;
                if (mFeedFrameTotalCount >= 2) {
                    if (0 == mFeedFrameSuccessCount) {
                        mRecognizeQueue.offer(false);  //两帧图片都检测到无人脸，直接报错
                    } else {
                        mFeedFrameFinish = true;
                    }
                }
                long endTime = System.currentTimeMillis(); //结束时间
                long runTime = endTime - startTime;
                Log.i("]time", String.format("feedfream %d ms", runTime));
            }
        }

        @Override
        public void interrupt() {
            Log.e(FeedFrameThreadTAG, "interrupt");
            if (!resumeRotation()) {
                Log.e(FeedFrameThreadTAG, "setAddFaceConfig1 failed");
            }
            isInterrupt = true;
            super.interrupt();
        }
    }

    private class RecognizeThread1 extends Thread {
        private final String RecognizeThreadTAG = RecognizeThread1.class.getSimpleName();
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    Log.i(RecognizeThreadTAG, "take() enter");
                    byte[] detectionResult = mDetectResultQueue.take();
                    Log.i(RecognizeThreadTAG, "take() leave");

                    Log.d(RecognizeThreadTAG, "mDetectResultQueue.isLocalGroupExist");
                    if (isLocalGroupExist) {
                        Boolean ret = false;
                        Log.d(RecognizeThreadTAG, "mDetectResultQueue.recognize");

                        FacePassRecognitionResult[] recognizeResult = mFacePassHandler.recognize(GROUP_NAME, detectionResult);

                        if (recognizeResult != null && recognizeResult.length > 0) {  //识别成功
                            Log.e(RecognizeThreadTAG, "识别有结果");
                            long endTime = System.currentTimeMillis(); //结束时间
                            long runTime = endTime - mStartTime;
                            Log.i("]time", String.format("recognize %d ms", runTime));
                            for (FacePassRecognitionResult result : recognizeResult) {
                                if (FacePassRecognitionResultType.RECOG_OK == result.facePassRecognitionResultType) {
                                    Log.e(RecognizeThreadTAG, "识别成功");
                                    ret = true;
                                }
                                showRecognizeReslt(result);  //打印识别结果
                            }
                        } else {
                            Log.e(RecognizeThreadTAG, "识别失败");
                        }
                        mRecognizeTotalCount++;
                        if (mFeedFrameFinish) {
                            if (ret) {  //识别成功
                                mRecognizeQueue.offer(true);
                            }
                            else if (mRecognizeTotalCount >= mFeedFrameSuccessCount) {  //识别失败次数等于送识别次数
                                mRecognizeQueue.offer(false);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                mTimeLock = false;
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    private void showRecognizeReslt(FacePassRecognitionResult result) {
        Log.e(TAG, "err code:" + result.facePassRecognizeErrorCode + ",track id:" + result.trackId
                + ",type:" + result.facePassRecognitionResultType);
        Log.e(TAG, "score:" + result.detail.searchScore + ",threashold:" + result.detail.searchThreshold
                + ",ext:" + result.detail.searchExtThreshold + "\nliveness score:" + result.detail.livenessScore + ",threadshold:"
                + result.detail.livenessThreshold + "\nmouth valid:" + result.detail.mouthOccAttr.is_valid + ",status:"
                + result.detail.mouthOccAttr.mouth_occ_status);
    }

    private void startFeedFrameRecognize() {
        mFeedFrameFinish = false;
        mFeedFrameSuccessCount = 0;
        mFeedFrameTotalCount = 0;
        mFeedFrameResult.clear();
        mRecognizeTotalCount = 0;
        mRecognizeQueue.clear();  //清空识别结果通知队列
    }

    private void stopFeedFrameRecognize() {

    }
}
