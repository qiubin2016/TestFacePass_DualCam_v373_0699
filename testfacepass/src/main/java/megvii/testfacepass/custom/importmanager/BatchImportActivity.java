package megvii.testfacepass.custom.importmanager;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lee.zbardemo.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
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
import mcv.facepass.types.FacePassFace;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionResultType;
import megvii.testfacepass.MainActivity;
import megvii.testfacepass.R;
import megvii.testfacepass.camera.CameraPreviewData;
import megvii.testfacepass.custom.dataclass.DisplayData;
import megvii.testfacepass.custom.db.DbOpt;
import megvii.testfacepass.custom.httpserver.HttpServer;
import megvii.testfacepass.custom.httpserver.ImageData;
import megvii.testfacepass.utils.FileUtil;

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
    private static final int REQUEST_CODE_CHOOSE_PICK = 1;
    private ExecutorService mExecutorService;
    private Future mFuture;
    private Toast toast;

    // view
    private EditText mFaceImagePath;
    private Button mBtnDetectFace;
    private Button mBtnChoosePic;
    private ImageView mImageUser;
    private TextView mTextResult;
    private Button mBtnAddFace;
    private Button mBtnRegister;
    private Button mBtnGet;
    private Button mBtnDelete;
    private Button mBtnRename;
    private Button mBtnDeleteFile;
    private Button mBtnDeleteSuccess;
    private Button mBtnDeleteFailed;
    private Button mButtonImport;
    private Button mBtnTest;
    private Button mBtnExtract;
    private Button mBtnDbAdd, mBtnDbDelete, mBtnDbUpdate, mBtnDbQuery;
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
//    ArrayBlockingQueue<byte[]> mDetectResultQueue;
    ArrayBlockingQueue<RecognizeData> mDetectResultQueue;
//    ArrayBlockingQueue<CameraPreviewData> mFeedFrameQueue;
    ArrayBlockingQueue<FeedFrameData> mFeedFrameQueue;
    ArrayBlockingQueue<Boolean> mRecognizeQueue;  //识别成功通知队列
    RecognizeThread1 mRecognizeThread1;
    FeedFrameThread1 mFeedFrameThread1;
    private long mStartTime = 0;
    private boolean mTimeLock = false;
    private final int mCamerarotation = 0;  //照片都是0度，无旋转
    private int mCamerarotationBak = 0;  //保存原来Config.rotation
    private boolean mIsModify = false;   //记录是否进行了facepass config调整
    /* SDK 实例对象 */
    private FacePassHandler mFacePassHandler;
    private boolean isLocalGroupExist = false;
    private ArrayList<Boolean> mFeedFrameResult;
//    private ArrayList<Boolean> mRecognizeResult;
    private boolean mFeedFrameFinish;
    private int mFeedFrameTotalCount, mFeedFrameSuccessCount, mRecognizeTotalCount;
    private HttpServer mHttpServer;
    private boolean mFirstRun = false;
    private Handler mAndroidHandler;
    private static final int MSG_SHOW = 1;
    private static final int MSG_CLEAR = 2;
    private Timer mTimer;
    private long mClearImageCount;
    private boolean mEnableSetConfig = false;  //标记是否需要在定时器内更改rotation 这里用来检测图片rotation应为0
    private ImageUtils.JpgMsg mJpgMsg = null;    //缓存无人脸图片数据

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
        Log.e(TAG, "onResume-----------------------");
        checkGroup();  //检查底库是否存在
        startRecognize();
        //启动http server
        if (mHttpServer == null) {
            mHttpServer = new HttpServer();
        }
        try {
            // 启动web服务
            if (!mHttpServer.isAlive()) {
                mHttpServer.start();
            }
            Log.i(TAG, "The server started.");
        } catch (Exception e) {
            mHttpServer.stop();
            Log.e(TAG, "The server could not start. e = " + e.toString());
        }
        super.onResume();
        Log.e(TAG, "register begin");
        EventBus.getDefault().register(this);
        Log.e(TAG, "register end");
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause-----------------------");
        stopRecognize();
        mDetectResultQueue.clear();
        //停止http server
        if (null != mHttpServer) {
            if (mHttpServer.isAlive()) {
                mHttpServer.stop();
            }
        }
        super.onPause();
        Log.e(TAG, "unregister begin");
        EventBus.getDefault().unregister(this);
        Log.e(TAG, "unregister end");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (null != mTimer) {  //停止定时器
            mTimer.cancel();
            mTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放
        ImportFileManager.getInstance().release();
        release();
    }

    private void initView() {
        mFaceImagePath = (EditText) findViewById(R.id.et_face_image_path);
        mBtnDetectFace = (Button) findViewById(R.id.btn_detect_face);
        mBtnDetectFace.setOnClickListener(this);
        mBtnChoosePic = (Button) findViewById(R.id.btn_choose_picture);
        mBtnChoosePic.setOnClickListener(this);
        Button buttonBack = (Button) findViewById(R.id.btn_import_back);
        buttonBack.setOnClickListener(this);
        mImageUser = (ImageView) findViewById(R.id.image_user);  //显示用户缩略图
        mTextResult = (TextView) findViewById(R.id.text_result);  //显示识别结果
//        mTextResult.setTextSize(20);  //设置字体大小
//        mTextResult.setTextColor(Color.BLUE);  //设置字体颜色
//        mTextResult.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));  //设置粗体
        mBtnAddFace = (Button) findViewById(R.id.btn_addface);
        mBtnAddFace.setOnClickListener(this);
        mBtnRegister = (Button) findViewById(R.id.btn_register);
        mBtnRegister.setOnClickListener(this);
        mBtnGet = (Button) findViewById(R.id.btn_get);
        mBtnGet.setOnClickListener(this);
        mBtnDelete = (Button) findViewById(R.id.btn_delete);
        mBtnDelete.setOnClickListener(this);
        mBtnRename = (Button) findViewById(R.id.btn_rename);
        mBtnRename.setOnClickListener(this);
        mBtnDeleteFile = (Button) findViewById(R.id.btn_delete_file);
        mBtnDeleteFile.setOnClickListener(this);
        mBtnDeleteSuccess = (Button) findViewById(R.id.btn_delete_success);
        mBtnDeleteSuccess.setOnClickListener(this);
        mBtnDeleteFailed = (Button) findViewById(R.id.btn_delete_failed);
        mBtnDeleteFailed.setOnClickListener(this);
        mButtonImport = (Button) findViewById(R.id.btn_import);
        mButtonImport.setOnClickListener(this);
        mBtnTest = (Button) findViewById(R.id.btn_test);
        mBtnTest.setOnClickListener(this);
        mBtnExtract = (Button) findViewById(R.id.btn_extract);
        mBtnExtract.setOnClickListener(this);
        mBtnDbAdd = (Button) findViewById(R.id.btn_db_add);  //数据库增加数据按钮
        mBtnDbAdd.setOnClickListener(this);
        mBtnDbDelete = (Button) findViewById(R.id.btn_db_delete);  //数据库删除数据按钮
        mBtnDbDelete.setOnClickListener(this);
        mBtnDbUpdate = (Button) findViewById(R.id.btn_db_update);  //数据库更新数据按钮
        mBtnDbUpdate.setOnClickListener(this);
        mBtnDbQuery = (Button) findViewById(R.id.btn_db_query);  //数据库查询数据按钮
        mBtnDbQuery.setOnClickListener(this);
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

//        mDetectResultQueue = new ArrayBlockingQueue<byte[]>(5);
        mDetectResultQueue = new ArrayBlockingQueue<RecognizeData>(5);
//        mFeedFrameQueue = new ArrayBlockingQueue<CameraPreviewData>(1);
        mFeedFrameQueue = new ArrayBlockingQueue<FeedFrameData>(10);
        mRecognizeQueue = new ArrayBlockingQueue<Boolean>(1);
        mFeedFrameResult = new ArrayList<Boolean>(2);
//        mRecognizeResult = new ArrayList<Boolean>(2);
        mFeedFrameThread1 = new FeedFrameThread1();
        mRecognizeThread1 = new RecognizeThread1();

        mAndroidHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_SHOW:
                        msgShow(msg.obj);
                        break;
                    case MSG_CLEAR:
                        mImageUser.setImageBitmap(null);  //清空缩略图
                        mTextResult.setText("");
                        break;
                }
            }
        };
        mTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (0 != mClearImageCount) {
                    mClearImageCount--;
                    if (0 == mClearImageCount) {
                        mAndroidHandler.sendEmptyMessage(MSG_CLEAR);  //发消息清空缩略图
                    }
                }
                if (!mEnableSetConfig) {  //调整rotation为0
                    //调整rotation 从0-->90
                    if (!modifyRotation()) {
                        Log.e(TAG, "setConfig failed");
                    } else {
                        Log.e(TAG, "setConfig success");
                        mEnableSetConfig = false;
                    }
                }
            }
        };
        mTimer.schedule(task, 0, 100);  //100ms 周期定时器


    }

    @Override
    public void onClick(View view) {
        Log.i(TAG, "===============================onclicked!" + view.getId());
        switch (view.getId()) {
            case R.id.btn_import:   // 点击导入数据按钮
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
            case R.id.btn_addface:  //入库
                addFace();
                break;
            case R.id.btn_register: //向后台注册用户
                register();
                break;
            case R.id.btn_get:
                get();
                break;
            case R.id.btn_delete:
                delete();
                break;
            case R.id.btn_rename:
                rename();
                break;
            case R.id.btn_delete_file:
                deleteFile();
                break;
            case R.id.btn_delete_success:
                deleteSuccess();
                break;
            case R.id.btn_delete_failed:
                deleteFailed();
                break;
            case R.id.btn_test:
                test();
                break;
            case R.id.btn_extract:
//                extract();
                isFaceExist("/storage/emulated/0/DCIM/Camera/qiub4.jpg");
                break;
            case R.id.btn_db_add:
                dbAdd();
                break;
            case R.id.btn_db_delete:
                dbDelete();
                break;
            case R.id.btn_db_update:
                dbUpdate();
                break;
            case R.id.btn_db_query:
                dbQuery();
                break;
            case R.id.btn_import_back:
                // 释放
                ImportFileManager.getInstance().release();
                release();
                finish();
                break;
            case R.id.btn_choose_picture:
                Intent intentFromGallery = new Intent(Intent.ACTION_GET_CONTENT);
                intentFromGallery.setType("image/*"); // 设置文件类型
                intentFromGallery.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intentFromGallery, REQUEST_CODE_CHOOSE_PICK);
                } catch (ActivityNotFoundException e) {
                    toast("请安装相册或者文件管理器");
                }
                break;
            case R.id.btn_detect_face:
                detectFace();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            //从相册选取照片后读取地址
            case REQUEST_CODE_CHOOSE_PICK:
                if (resultCode == RESULT_OK) {
                    String path = "";
                    Uri uri = data.getData();
                    String[] pojo = {MediaStore.Images.Media.DATA};
                    CursorLoader cursorLoader = new CursorLoader(this, uri, pojo, null, null, null);
                    Cursor cursor = cursorLoader.loadInBackground();
                    if (cursor != null) {
                        cursor.moveToFirst();
                        path = cursor.getString(cursor.getColumnIndex(pojo[0]));
                    }
                    if (!TextUtils.isEmpty(path) && "file".equalsIgnoreCase(uri.getScheme())) {
                        path = uri.getPath();
                    }
                    if (TextUtils.isEmpty(path)) {
                        try {
                            path = FileUtil.getPath(getApplicationContext(), uri);
                        } catch (Exception e) {
                        }
                    }
                    if (TextUtils.isEmpty(path)) {
                        toast("图片选取失败！");
                        return;
                    }
                    if (!TextUtils.isEmpty(path)) {
                        mFaceImagePath.setText(path);
                    }
                }
                break;
        }
    }

    private void msgShow(Object obj) {
        if (null != obj) {
            mClearImageCount = 10;  //10 * 100ms后清空缩略图
            Bitmap bitmap = null;
            DisplayData displayData = (DisplayData) obj;
            FacePassRecognitionResult result = displayData.getResult();
            try {
                bitmap = mFacePassHandler.getFaceImage(result.faceToken);  //获取缩略图
            } catch (FacePassException e) {
                e.printStackTrace();
            }
            mImageUser.setImageBitmap(bitmap);  //显示缩略图
            //id score errcode
            final long trackId = result.trackId;
            final float score = result.detail.searchScore;
            final float extScore = result.detail.searchExtThreshold;
            final int errCode = result.facePassRecognizeErrorCode;
            String text = "";
            StringBuilder stringBuilder;
            if (trackId > 0) {
                text += "ID: " + trackId + "\n";
            }
            if (score > 0) {
                text += "识别分: " + score + "\n";
            }
            if (extScore > 0) {
//                text += "戴口罩识别分: " + extScore + "\n";
            }
            if (0 != errCode) {
                text += "错误码: " + errCode + "\n";
            }
            mTextResult.setText(text);
            /*
                    Log.e(TAG, "err code:" + result.facePassRecognizeErrorCode + ",track id:" + result.trackId
                    + ",type:" + result.facePassRecognitionResultType);
            Log.e(TAG, "score:" + result.detail.searchScore + ",threashold:" + result.detail.searchThreshold
                    + ",ext:" + result.detail.searchExtThreshold + "\nliveness score:" + result.detail.livenessScore + ",threadshold:"
                    + result.detail.livenessThreshold + "\nmouth valid:" + result.detail.mouthOccAttr.is_valid + ",status:"
                    + result.detail.mouthOccAttr.mouth_occ_status);*/
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
//                    if (!modifyRotation()) {
//                        Log.e(TAG, "setConfig failed");
//                        return;
//                    }
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
                                toast("图片不存在！path:" + imageFile.getPath() + ",name:" + picName);
                                continue;
                            }
                            String imagePath = imageFile.getPath();
                            Log.i(TAG, "imagePath:" + imagePath);
                            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                            if (null != bitmap) {
                                Log.i(TAG, "config:" + bitmap.getConfig().toString());
                                boolean isProcessed = false;  //图片是否已经被处理
                                for (File file : successFiles) {
                                    if (picName.equals(file.getName())) {
                                        isProcessed = true;  //保存成功图片目录中找到相同图片
                                        Log.e(TAG, "保存成功图片目录中找到相同图片");
                                        break;
                                    }
                                }
                                if (!isProcessed) {
                                    for (File file : failedFiles) {
                                        if (picName.equals(file.getName())) {
                                            isProcessed = true;  //保存失败图片目录中找到相同图片
                                            Log.e(TAG, "保存失败图片目录中找到相同图片");
                                            break;
                                        }
                                    }
                                }
                                if (isProcessed) {  //确认已被处理，更新进度
                                    processSuccess();  //更新进度
//                                    imageFile.delete();  //删除图片
                                    Log.e(TAG, "删除图片1：" + picName);
                                    continue;  /*=====================success=====================*/
                                }
                                final int widthPic = bitmap.getWidth();
                                final int heightPic = bitmap.getHeight();
                                Log.i(TAG, "width:" + widthPic + ",height:" + heightPic);
                                if ((widthPic <= WIDTH_MAX) && (heightPic <= HEIGHT_MAX)) {  //检查照片分辨率
                                    try {
                                        boolean isExist = false;
//                                        isExist = isFaceExist(mFacePassHandler, bitmap, widthPic, heightPic, picName);  //图片是否有人脸，是否在人脸库中
                                        if (isExist) {  //人脸重复
                                            processSuccess();  //更新进度
                                            // 保存图片到重复目录中
                                            saveBitmaptoDirectory(FileUtils.getBatchImportRepeatDirectory(), picName, bitmap);
//                                            imageFile.delete();  //删除图片
                                            Log.e(TAG, "重复人脸！删除图片2：" + picName);
                                            continue;  /*=====================success=====================*/
                                        }
                                        Log.e("addface", "name:" + picName);
                                        FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);  //人脸图片入库
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
                                                Log.e(TAG, "入库成功" + ",name:" + picName);
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
                                                Log.e(TAG, "no face!" + ",name:" + picName);
                                            } else {
                                                Log.e(TAG, "quality problem！" + ",name:" + picName);
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
//                                imageFile.delete();  //删除图片
                                Log.e(TAG, "删除图片：" + imageFile.getName());
                            }
                            processFailed();  //更新进度
                        }
                    }
                    //恢复rotation
//                    if (!resumeRotation()) {
//                        Log.e("addface", "setAddFaceConfig1 failed");
//                    }
                }
                Log.i(TAG, "addFace finish！");
                // 导入完成
                endImport(mFinishCount, mSuccessCount, mFailCount, mTotalCount);
            }
        });
    }

    private boolean isFaceExist(String imagePath) {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        //调整rotation
//        if (!modifyRotation()) {
//            Log.e(TAG, "setConfig failed");
//            return ret;
//        }
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        ret = isFaceExist(mFacePassHandler, bitmap, bitmap.getWidth(), bitmap.getHeight(), FileUtils.getTimestampName());
        //恢复rotation
//        if (!resumeRotation()) {
//            Log.e("addface", "setAddFaceConfig1 failed");
//        }
        return ret;
    }

    private boolean isFaceExist(FacePassHandler facePassHandler, Bitmap bitmap, final int width, final int height, final String name) {
        boolean ret = false;
        Log.i("addface", "bitmapToNv21");
        byte[] bytes = ImageUtils.bitmapToNv21(bitmap, width, height);
        Log.i("addface", "bitmap2RGB:" + bytes.length + ",width:" + width + ",height:" + height);
        startFeedFrameRecognize();  //初始化检测和识别相关的数据
        facePassHandler.reset();  //
        //两帧
        addRgbFrame(bytes.clone(), width, height, name);
        Log.i(TAG, "addRgbFrame");
        addRgbFrame(bytes.clone(), width, height, name);
        Log.i(TAG, "addRgbFrame");
        try {
            Log.e(TAG, "mRecognizeQueue.take() enter");
            ret = mRecognizeQueue.take();
            Log.e(TAG, "mRecognizeQueue.take() leave");
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
            isSuccess = mFacePassHandler.deleteLocalGroup(GROUP_NAME);  //删除组
            Log.i(TAG, "deleteLocalGroup:" + isSuccess);
            if (isSuccess) {
                isLocalGroupExist = false;
                isSuccess = mFacePassHandler.createLocalGroup(GROUP_NAME);  //创建组
                if (isSuccess) {
                    isLocalGroupExist = true;
                }
                Log.i(TAG, "createLocalGroup:" + isSuccess);
            }
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        toast("deleteLocalGroup:" + (isSuccess ?"success" : "failed"));
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
                        //新文件名：group name + MMddHHmm + 00001 + 图片后缀；例如：2020071509412500001-facepass.jpg
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
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                deleteFiles(FileUtils.getBatchImportDirectory());
            }
        });
    }

    private void deleteSuccess() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                deleteFiles(FileUtils.getBatchImportSuccessDirectory());
            }
        });
    }

    private void deleteFailed() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                deleteFiles(FileUtils.getBatchImportFailedDirectory());
            }
        });
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
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg");
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg");

                if (!jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg")) {
                    jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg");
                }
                jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg");
                jpgFeedFrame("/storage/emulated/0/Face-Import/1111-facepass.jpg");
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg");
                jpgFeedFrame("/storage/emulated/0/Face-Import/2020071601394600025-facepass.jpg");
                Log.i(TAG, "test finish!");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = null;
                        int width;
                        int height;
                        for (int i = 0; i < 10; i++) {
                            Log.e(TAG, "=================i:" + i);
                            bitmap = BitmapFactory.decodeFile("/storage/emulated/0/Face-Import/1111-facepass.jpg");
                            isFaceExist(mFacePassHandler, bitmap, 720, 1280, String.valueOf(i));
                        }
                        Log.e(TAG, "=================58");
                        bitmap = BitmapFactory.decodeFile("/storage/emulated/0/Face-Import/2020071601394600066-facepass.jpg");
                        width = bitmap.getWidth();
                        height = bitmap.getHeight();
                        Log.i(TAG, "w:" + width + ",h:" + height);
                        isFaceExist(mFacePassHandler, bitmap, width, height, "66");
                        Log.e(TAG, "=================thread exit");
                    }
                }).start();
            }
        });
    }

    private void test() {
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
        mFuture = mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                String str;

                str = "71 4a 31 dd 9b d2 3d 58 65 10 b5 d6 58 0b 1e 83 f5 26 c1 bb 5a 21 f5 e6 e6 19 6c 65 bb b9 e1 73 77 da 3c ee 2f ef 45 96 85 2f 52 78 3f 59 2e b9 f7 41 eb 05 eb 55 55 96 1b 93 ab d4 2b 6d 81 70 ef 91 1b 71 a8 cb 14 26 39 34 63 89 ad 9e c8 70 0d b9 54 88 80 56 62 74 59 fc c1 93 83 eb 95 bc d0 05 be 7e dd ba 4a c4 48 4a 41 ff 37 8d 0b ec df 36 e2 69 a3 91 bc 93 8e db 8a 3d 73 59 dc 85 a0 cf 48 5c ef 5b cb f7 b9 d9 29 26 15 1d cc f5 32 a6 42 a2 4a 61 54 a2 8f 9f e6 8f 12 8a 10 fb d6 b0 9f fb 3d 4f 65 3c 5e 3e f2 9e 26 01 b8 a2 60 12 4b a5 54 ae 58 cf bb 5c 24 32 d1 c8 5a e6 d2 82 91 a3 85 c3 aa d1 b1 a5 b5 62 8d 51 14 34 48 b8 74 22 3f cc a7 34 6e 5c 68 0b d1 e3 67 ee 03 fc 91 b1 37 41 af 17 af 8a 0e 6f f9 5f f2 b6 7a d5 33 f4 25 14 47 e3 4c 58 fe 40 b0 ce 43 aa 29 5e 1f dd 10 0e 25 94 5a 43 e7 c1 84 b4 03 39 81 75 4b fb dc 1a d8 13 01 5f 53 ae a5 ff 3b b6 54 1f f6 b9 26 bc 6a 71 5d 6c cb a2 58 24 8d 11 82 ff ac 06 5b a4 bb 83 b9 ca 3d ae 04 e1 d9 81 81 e4 41 ef f6 43 62 bf 70 45 ad 4e 82 21 66 8c e1 3f 81 10 a8 c0 c1 d6 ba 25 65 22 24 74 5e ce 5e 29 17 65 2a 77 ca 49 e7 5a 08 2e c6 e9 0e 19 8d c3 1f f8 40 a9 b9 88 f2 6e ab 2c 21 b4 77 9e 4b 1b e2 ee c6 c6 9c 45 46 d0 5e 81 12 56 8d fc bb a5 f2 48 f3 b6 71 d6 10 60 2a 2e dd bb 03 61 29 47 cb b0 44 8c f1 e7 1f 8d 53 58 5f 63 ad 0f c0 f0 48 94 11 2d 0c 92 5e 1e c2 0e a8 17 63 ca a0 dd 05 d8 d4 6a 74 a7 25 38 8c 37 32 64 81 35 af ea 56 8f 87 1b 3d e6 23 af ed 19 0f 4b 0e 58 57 8a 2e 3f 4c 2a ee 3e 42 e1 65 71 e4 97 bf cd 22 c8 96 28 dc 9c e1 f6 73 ff b5 83 23 f1 d2 59 ";
                InsertFeatureBindGroup(str);
                str = "48 12 7e d9 60 fe 9e 4c 63 09 af 3d 5d c9 dd b5 30 30 e3 bc 56 07 27 cf 1c 1c 84 95 bd a3 dd 59 a3 02 11 a5 2d cb 8a a9 77 cf ad 5e 06 be c8 29 fd 5a 1e 39 2e 9f 0c 04 39 a7 00 d2 3a 89 8f ac cb 50 f3 80 57 e5 f9 c6 cf d0 32 78 7d 8f cf 90 ef f0 4d 80 a7 a5 70 b9 63 06 39 b4 a3 c6 8b 41 e5 1f ad 82 e5 85 af 2d 20 6e 6c 0f eb 95 01 02 7d c5 db 81 4a 94 75 8b af 0a 71 3d 77 b2 da e4 9d d4 25 8a 18 b7 25 18 45 f9 d2 06 1f 24 32 20 2e 4a 6a 8f b1 43 4c a0 64 47 1a 99 cd 75 c8 18 2c 89 6d da 09 a2 91 30 94 20 0f 51 19 37 47 5d 28 cc e4 41 4c 8f 5a ec 61 6e 3a ff f8 e2 80 24 dd 52 9a 10 bc 17 54 38 41 bf b2 5b 56 55 3b 16 ba 9e 4e fc 07 9c b7 c6 d1 b7 8a d4 89 24 5e 04 0f 22 68 6d 47 52 7a 2d ae 47 1d b3 d0 52 c1 52 5d 23 1f e3 08 e5 91 e9 ac 55 e0 b9 46 00 56 9f cd 51 fa f2 e4 d0 1f 79 a5 6d 1b e9 71 aa fd 35 65 b9 68 06 f1 05 f5 d1 bd 69 3c 54 5f d1 1a 51 92 e4 0c b3 f5 5c 43 97 ac 67 fa 48 b6 1a f4 a5 51 ce bd 2a b5 a0 6b 4b 70 3b 95 57 78 d8 3f 73 bb 3b bf c9 f8 a9 61 b3 ac 85 6a 84 97 08 9f ac ef 22 7e f4 76 05 28 20 f4 d4 94 26 89 43 a3 a5 95 dd 1f 73 0b 44 f3 02 25 44 d2 8d 68 fd d7 06 58 c7 c4 14 a1 63 52 b7 3c 6b 56 e9 7f 63 8d 91 1b ad 13 19 21 da 42 8a 61 2e fc fe fb 63 5a 25 7f b8 0c 55 fe 55 8c 3c e2 85 d0 26 f2 42 15 04 d4 46 ea 78 83 bf eb fe fb 57 9b ba ef b2 78 e0 e4 3f 72 d4 02 ff fd 6d 4d ff 0f c4 9c cc af 04 b7 c2 08 86 1a 9f a8 bb 14 54 76 2b 3d 85 81 d2 ba 11 b2 6d 82 06 3f 11 f2 70 dc 01 28 78 f8 79 5d 71 d9 e2 a3 e7 ef 1b 57 cd 69 53 c5 99 56 c6 c9 d2 b8 f0 f8 47 e8 b0 b7 3d a1 bc 30 07 00 35 ";
                InsertFeatureBindGroup(str);

                FacePassConfig config = mFacePassHandler.getConfig();
                Log.e(TAG, "rotation:" + config.rotation);
//        jpgFeedFrame("/storage/emulated/0/DCIM/Camera/qiub4_small.jpg", 589, 738, 0);
//                jpgFeedFrame("/storage/emulated/0/DCIM/Camera/qiub4.jpg", 2076, 2432, 0);
                jpgFeedFrame("/storage/emulated/0/DCIM/Camera/qiub1.jpg");
                jpgFeedFrame("/storage/emulated/0/DCIM/Camera/qiub1.jpg");
                Log.i(TAG, "jpgFeedFrame 1 end");
                jpgFeedFrame("/storage/emulated/0/MyImage/20200717090502-00000000.jpg");
                jpgFeedFrame("/storage/emulated/0/MyImage/1111-facepass.jpg");

//                jpgFeedFrame("/storage/emulated/0/MyImage/qiub4_small.jpg");  //qq截图
                jpgFeedFrame("/storage/emulated/0/MyImage/qiub1.jpg");
//                jpgFeedFrame("/storage/emulated/0/MyImage/qiub1_small.jpg");  //qq截图
                jpgFeedFrame("/storage/emulated/0/MyImage/qiub1_small1.jpg");  //微信截图
            }
        });
    }
    private boolean InsertFeatureBindGroup(String feature) {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        FacePassExtractFeatureResult result;
        boolean isSuccess = false;
        String faceTokenStr;
        feature = feature.replace(" ", "");  //将所有空格删除
        Log.i(TAG, "str:\n" + feature);
        byte[] bytes = Util.Companion.hexStr2ByteArray(feature);
        String faceToken;
        try {
            faceToken = mFacePassHandler.insertFeature(bytes, null);
            Log.i(TAG, "faceToken:" + faceToken);
            isSuccess = mFacePassHandler.bindGroup(GROUP_NAME, faceToken.getBytes());
            Log.i(TAG, "bindGroup:" + (isSuccess ? "success" : "failed"));
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        return (isSuccess);
    }

    private void extract() {
        ExtractFeature("/storage/emulated/0/DCIM/Camera/qiub4.jpg");
        ExtractFeature("/storage/emulated/0/DCIM/Camera/tanb.jpg");
    }

    private boolean ExtractFeature(String path) {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        //提取特征值
        FacePassExtractFeatureResult result;
        boolean isSuccess = false;
        String faceToken;
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        try {
            result = mFacePassHandler.extractFeature(bitmap);
            Log.i(TAG, "ret:" + result.result + ",len:" + result.featureData.length + ",data:\n" + Util.Companion.ByteArray2HexStr(result.featureData, true));
            faceToken = mFacePassHandler.insertFeature(result.featureData, null);
            Log.i(TAG, "faceToken:" + faceToken);
            isSuccess = mFacePassHandler.bindGroup(GROUP_NAME, faceToken.getBytes());
            Log.i(TAG, "bindGroup:" + (isSuccess ? "success" : "failed"));
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        return isSuccess;
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
    private boolean jpgFeedFrame(String path/*, final int width, final int height, final int rotation*/) {
        boolean ret = false;
        if (isFacePassHandlerNull()) {
            return ret;
        }
        Log.i(TAG, "path:" + path);
        FacePassImage image;
        try {
            ImageUtils.JpgMsg jpgMsg = ImageUtils.readJpgFileToByteArray(path/*, width, height*/);
            int len = jpgMsg.getByteArr().length;
            Log.i("FeedFrameThread", "len:" + len);
            if (len > 0) {
                image = new FacePassImage(jpgMsg.getByteArr(), jpgMsg.getWidth(), jpgMsg.getHeight(), 0, FacePassImageType.NV21);
            } else {
                return ret;
            }
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
        if (null == config) {
            Log.e(TAG, "FacePassConfig is null");
            return false;
        }
        mCamerarotationBak = config.rotation;
        config.rotation = 0;
        config.livenessEnabled = false;            //关闭活体 红外活体开关与活体开关，是两个独立的功能，同时打开，SDK只会使用一种活体算法，优先使用红外活体检测
        config.rgbIrLivenessEnabled = false;       //关闭红外活体功能
        try {
            mFacePassHandler.setConfig(config);
            mIsModify = true;
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
        if (mIsModify) {
            FacePassConfig config = mFacePassHandler.getAddFaceConfig();
            config.rotation = mCamerarotationBak;
            config.livenessEnabled = false;            //关闭活体 红外活体开关与活体开关，是两个独立的功能，同时打开，SDK只会使用一种活体算法，优先使用红外活体检测
            config.rgbIrLivenessEnabled = true;        //打开红外活体功能
            try {
                mFacePassHandler.setConfig(config);
                mIsModify = false;
            } catch (FacePassException e) {
                e.printStackTrace();
                return false;
            }
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
        mEnableSetConfig = true;  //需要在定时器修改rotation为0
    }

    private void stopRecognize() {
        mFeedFrameThread1.isInterrupt = true;
        mFeedFrameThread1.interrupt();
        mRecognizeThread1.isInterrupt = true;
        mRecognizeThread1.interrupt();
        //恢复rotation
        if (!resumeRotation()) {
            Log.e(TAG, "setAddFaceConfig failed");
        } else {
            Log.e(TAG, "setAddFaceConfig success");
        }
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
//                UserManager.addUserBindRoom();  //添加用户并绑定房间关系
                UserManager.updateUserInfo();  //修改用户信息
//                UserManager.getUserInfo();    //获取用户信息列表
                Log.i(TAG, "submit2");
            }
        });
    }

    private void addRgbFrame(byte[] data, final int width, final int height, String name) {
        boolean ret = mFeedFrameQueue.offer(new FeedFrameData(new CameraPreviewData(data.clone(), width, height,
                0, false), name));
        Log.i(TAG, "addRgbFrame1, ret:" + ret);
    }

    private class FeedFrameData {
        private CameraPreviewData cameraPreviewData;
        private String name;

        public FeedFrameData(CameraPreviewData cameraPreviewData, String name) {
            this.cameraPreviewData = cameraPreviewData;
            this.name = name;
        }

        public CameraPreviewData getCameraPreviewData() {
            return cameraPreviewData;
        }

        public String getName() {
            return name;
        }
    }
    private class RecognizeData {
        private byte[] data;
        private long trackId;
        private String name;
        RecognizeData(byte[] data, long trackId, String name) {
            this.data = data;
            this.trackId = trackId;
            this.name = name;
        }

        public byte[] getData() {
            return data;
        }

        public long getTrackId() {
            return trackId;
        }

        public String getName() {
            return name;
        }
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
                FeedFrameData feedFrameData = null;
                try {
                    Log.i(FeedFrameThreadTAG, "mFeedFrameQueue.take() enter");
//                    cameraPreviewData = mFeedFrameQueue.take();
                    feedFrameData = mFeedFrameQueue.take();
                    Log.i(FeedFrameThreadTAG, "mFeedFrameQueue.take() leave, name:" + feedFrameData.getName());
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
                    FacePassConfig config = mFacePassHandler.getConfig();
                    Log.i(FeedFrameThreadTAG, "rotation:" + mCamerarotation + ",rotation1:" + config.rotation);
                    cameraPreviewData = feedFrameData.getCameraPreviewData();
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
                    FacePassFace facePassFace = detectionResult.faceList[0];
                    Log.e(FeedFrameThreadTAG, "有人脸" + ",trackId:" + facePassFace.trackId + ",errCode:" + facePassFace.facePassFeedFrameErrorCode
                    + ",roll:" + facePassFace.pose.roll + ",pitch:" + facePassFace.pose.pitch + ",yaw:" + facePassFace.pose.yaw + ",blur:" + facePassFace.blur
                    + ",ifIRPassed:" + facePassFace.ifIRPassed + ",mouth:" + facePassFace.mouthOccAttr.mouth_occ_status);
                    if (detectionResult.message.length != 0) {
                        Log.e(FeedFrameThreadTAG, "送识别");
                        mFeedFrameSuccessCount++;
                        Log.e(FeedFrameThreadTAG, "mDetectResultQueue.offer");
                        mDetectResultQueue.offer(new RecognizeData(detectionResult.message,
                                facePassFace.trackId, feedFrameData.getName()));  //添加到识别队列
                        mTimeLock = true;  //锁定mStartTime，直到recognize完成
                        Log.i("]time", "mDetectResultQueue.offer");
                    } else {
                        Log.e(FeedFrameThreadTAG, "不送识别");
                    }
                }
                mFeedFrameTotalCount++;
                if (mFeedFrameTotalCount >= 2) {
                    if (0 == mFeedFrameSuccessCount) {
                        Log.e(FeedFrameThreadTAG, "mRecognizeQueue.offer(false)");
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
//            if (!resumeRotation()) {
//                Log.e(FeedFrameThreadTAG, "setAddFaceConfig1 failed");
//            }
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
                    Log.e(RecognizeThreadTAG, "mDetectResultQueue.take() enter");
//                    byte[] detectionResult = mDetectResultQueue.take();
                    RecognizeData detectionResult = mDetectResultQueue.take();
                    Log.e(RecognizeThreadTAG, "mDetectResultQueue.take() leave, name:" + detectionResult.getName());

                    Log.d(RecognizeThreadTAG, "mDetectResultQueue.isLocalGroupExist:" + isLocalGroupExist);
                    if (isLocalGroupExist) {
                        Boolean ret = false;
                        Log.d(RecognizeThreadTAG, "mDetectResultQueue.recognize");

                        FacePassRecognitionResult[] recognizeResult = mFacePassHandler.recognize(GROUP_NAME, detectionResult.getData());

                        if (recognizeResult != null && recognizeResult.length > 0) {  //识别成功
                            Log.e(RecognizeThreadTAG, "识别有结果");
                            long endTime = System.currentTimeMillis(); //结束时间
                            long runTime = endTime - mStartTime;
                            Log.i("]time", String.format("recognize %d ms", runTime));
                            for (FacePassRecognitionResult result : recognizeResult) {
                                if (FacePassRecognitionResultType.RECOG_OK == result.facePassRecognitionResultType) {
                                    Log.e(RecognizeThreadTAG, "识别成功");
                                    ret = true;
                                } else {
                                    Log.e(RecognizeThreadTAG, "识别失败1");
                                }
                                Message msg = new Message();  //识别结果，发送消息送UI显示
                                msg.what = MSG_SHOW;
                                msg.obj = new DisplayData(result);
                                mAndroidHandler.sendMessage(msg);
                                showRecognizeReslt(result);  //打印识别结果
                            }
                        } else {
                            Log.e(RecognizeThreadTAG, "识别失败");
                        }
                        mRecognizeTotalCount++;
                        if (mFeedFrameFinish) {
                            if (ret) {  //识别成功
                                Log.e(RecognizeThreadTAG, "mRecognizeQueue.offer(true)");
                                mRecognizeQueue.offer(true);
                            }
                            else if (mRecognizeTotalCount >= mFeedFrameSuccessCount) {  //识别失败次数等于送识别次数
                                Log.e(RecognizeThreadTAG, "mRecognizeQueue.offer(false)");
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
        mFeedFrameQueue.clear();  //清空FeedFrame队列
        mDetectResultQueue.clear();  //清空待识别队列
    }

    private void stopFeedFrameRecognize() {

    }

    private void dbAdd() {
        DbOpt.Companion.insert();
    }
    private void dbDelete() {
        DbOpt.Companion.delete();
    }
    private void dbUpdate() {
        DbOpt.Companion.update();
    }
    private void dbQuery() {
        DbOpt.Companion.query();
    }

    private void detectFace() {
        String imagePath = mFaceImagePath.getText().toString();
        if (TextUtils.isEmpty(imagePath)) {
            toast("请输入正确的图片路径！");
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            toast("图片不存在 ！");
            return;
        }
        String str = ImageUtils.readJpgFileToBase64(imagePath);
        Log.i(TAG, "base64 len:" + str.length());
        LogUtils.e(TAG, "base64:" + str);
        Log.i(TAG, "jpgFeedFrame 1 begin");
        //图片送检测
        jpgFeedFrame(imagePath);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void imageProc(ImageData data) {
        Log.i(TAG, "subscribe");
        Bitmap bitmap = BitmapFactory.decodeByteArray(data.getByteArr(), 0, data.getByteArr().length);  //解码
        if (null != bitmap) {
            boolean ret = false;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Log.i(TAG, "bitmapToNv21");
            byte[] bytes = ImageUtils.bitmapToNv21(bitmap, width, height);
            Log.i(TAG, "bitmap2RGB:" + bytes.length + ",width:" + width + ",height:" + height);
            if (isFacePassHandlerNull()) {
                return;
            }
            addRgbFrame(bytes.clone(), width, height, String.valueOf(data.getId()));  //插入一帧
            Log.i(TAG, "addRgbFrame");
            addRgbFrame(bytes.clone(), width, height, String.valueOf(data.getId()));  //插入一帧
            Log.i(TAG, "addRgbFrame");
            //插入无人脸图片
            if (null == mJpgMsg) {
                mJpgMsg = ImageUtils.readJpgFileToByteArray("/storage/emulated/0/MyImage/empty2.jpg");
            }
            if (mJpgMsg.getByteArr().length > 0) {
                addRgbFrame(mJpgMsg.getByteArr().clone(), mJpgMsg.getWidth(), mJpgMsg.getHeight(), String.valueOf(data.getId() + 10000));
                Log.i(TAG, "addRgbFrame no face");
            }
        } else {
            Log.e(TAG, "bitmap is null");
        }
    }
}
