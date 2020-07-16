package megvii.testfacepass.importmanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.types.FacePassAddFaceDetectionResult;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassDetectionResult;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassRecognitionResult;
import megvii.testfacepass.MainActivity;
import megvii.testfacepass.R;


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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_import);
        mContext = this;
        initView();
        initData();
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
        mButtonImport = (Button)findViewById(R.id.button_import);
        mButtonImport.setOnClickListener(this);
        mRelativeContent = (RelativeLayout) findViewById(R.id.relative_content);
        mRelativeImport = (RelativeLayout)findViewById(R.id.relative_progress);
        mRelativeFinish = (RelativeLayout)findViewById(R.id.relative_finish);
        mProgressBar = (ProgressBar)findViewById(R.id.progress_bar);
        mTextImportFinish = (TextView)findViewById(R.id.text_import_finish);
        mTextImportSuccess = (TextView)findViewById(R.id.text_import_success);
        mTextImportFailure = (TextView)findViewById(R.id.text_import_failure);
        mTextImportTotal = (TextView) findViewById(R.id.text_import_total);
        mTextFinish = (TextView)findViewById(R.id.text_finish);
        mTextFinishSuccess = (TextView)findViewById(R.id.text_finish_success);
        mTextFinishFailure = (TextView)findViewById(R.id.text_finish_failure);
        mTextFinishTotal = (TextView) findViewById(R.id.text_finish_total);
    }

    private void initData() {
        ImportFileManager.getInstance().setOnImportListener(this);
        if (null == mExecutorService) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
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
            FacePassHandler facePassHandler = MainActivity.mFacePassHandler;
            if (facePassHandler == null) {
                toast("FacePassHandle is null ! ");
                return;
            }
            Log.i(TAG, "===============================addFace");
            boolean isLocalGroupExist = false;
            String[] groups = facePassHandler.getLocalGroups();  //查询全部底库group
            if (groups != null && groups.length > 0) {
                for (String group : groups) {
                    if (GROUP_NAME.equals(group)) {  //找到group
                        isLocalGroupExist = true;
                        break;
                    }
                }
            } else {
                Log.i(TAG, "groups is null !");
            }
            if (!isLocalGroupExist) {  //group不存在，创建
                boolean isSuccess = false;
                try {
                    isSuccess = facePassHandler.createLocalGroup(GROUP_NAME);
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
                        BitmapFactory.Options op = new BitmapFactory.Options();
                        op.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, op);
                        if (null != bitmap) {
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
                                    FacePassConfig addFaceConfig = facePassHandler.getAddFaceConfig();
                                    addFaceConfig.rotation = 0;
                                    facePassHandler.setAddFaceConfig(addFaceConfig);
                                    android.util.Log.d("addface", "Bitmap2Bytes");
                                    byte[] bytes = ImageUtils.bitmapToNv21(bitmap, widthPic, heightPic);
//                                    byte[] bytes = ImageUtils.bitmap2RGB(bitmap);  //Bitmap2Bytes(bitmap);
//                                    byte[] bytes = Bitmap2Bytes(bitmap);
//                                    byte[] bytes = ImageUtils.getNV21(widthPic, heightPic, bitmap);
                                    ImageUtils.saveToJpg(bytes, widthPic, heightPic);
                                    ImageUtils.saveToFile(bytes, widthPic, heightPic);
                                    android.util.Log.d("addface", "bitmap2RGB:" + bytes.length + ",width:" + widthPic + ",height:" + heightPic);
                                    FacePassImage image = new FacePassImage(bytes, widthPic, heightPic,
                                            0, FacePassImageType.NV21);
                                    //FacePassImage(framePair.first.nv21Data, framePair.first.width, framePair.first.height, cameraRotation, FacePassImageType.NV21);
                                    /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
                                    android.util.Log.d("addface", "feedFrame");
                                    FacePassDetectionResult detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);
                                    FacePassAddFaceDetectionResult addFaceDetectionResult = facePassHandler.addFaceDetect(image);
                                    Log.i(TAG, "addFaceDetectionResult.faceList.length:" + addFaceDetectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            90, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            180, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            270, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, heightPic, widthPic,
                                            0, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, heightPic, widthPic,
                                            90, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, heightPic, widthPic,
                                            180, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, heightPic, widthPic,
                                            270, FacePassImageType.NV21);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            0, FacePassImageType.RGB);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            90, FacePassImageType.RGB);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);
                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            180, FacePassImageType.RGB);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            270, FacePassImageType.RGB);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            0, FacePassImageType.GRAY);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            90, FacePassImageType.GRAY);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            180, FacePassImageType.GRAY);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

                                    image = new FacePassImage(bytes, widthPic, heightPic,
                                            270, FacePassImageType.GRAY);
                                    detectionResult = facePassHandler.feedFrame(image);  //人脸检测
                                    Log.i(TAG, "detectionResult.faceList.length:" + detectionResult.faceList.length);

//                                    if (detectionResult != null && detectionResult.faceList.length > 0) {  //有合法人脸数据
                                        FacePassRecognitionResult[] recognizeResult = facePassHandler.recognize(GROUP_NAME, detectionResult.message);  //识别
                                        if (recognizeResult != null && recognizeResult.length > 0) {  //识别成功
                                            processSuccess();  //更新进度
                                            imageFile.delete();  //删除图片
                                            Log.e(TAG, "重复人脸！删除图片2：" + imageFile.getName());
                                            continue;  /*=====================success=====================*/
                                        }
                                        android.util.Log.d("addface", "addFace");
                                        FacePassAddFaceResult result = facePassHandler.addFace(bitmap);  //人脸图片入库
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
                                                Log.i(TAG, "addFace success");
                                                String faceToken = new String(result.faceToken);
                                                String bindStr = "";
                                                try {
                                                    isSuccess = facePassHandler.bindGroup(GROUP_NAME, result.faceToken);
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
//                                    } else
//                                    {
//                                        Log.e(TAG, "无人脸！");
//                                        continue;
//                                    }
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
            }
            Log.i(TAG, "addFace finish！");
            // 导入完成
            endImport(mFinishCount, mSuccessCount, mFailCount, mTotalCount);
            }
        });
    }
    private void delete() {
        FacePassHandler facePassHandler = MainActivity.mFacePassHandler;
        if (facePassHandler == null) {
            toast("FacePassHandle is null ! ");
            return;
        }
        boolean isSuccess = false;
        try {
            isSuccess = facePassHandler.deleteLocalGroup(GROUP_NAME);
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "delete:" + isSuccess);
        toast("delete:" + isSuccess);
    }
    private void get() {
        FacePassHandler facePassHandler = MainActivity.mFacePassHandler;
        if (facePassHandler == null) {
            toast("FacePassHandle is null ! ");
            return;
        }
        int num = 0;
        try {
            num =  facePassHandler.getLocalGroupFaceNum(GROUP_NAME);
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
    private void toast(String msg) {
//        Toast.makeText(BatchImportActivity.this, msg, Toast.LENGTH_SHORT).show();
        //优化Toast显示
        Context context = BatchImportActivity.this;
        int duration = Toast.LENGTH_SHORT;
        if(toast == null){
            toast = Toast.makeText(context, msg, duration);
        }else{
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

}
