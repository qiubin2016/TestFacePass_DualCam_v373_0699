package megvii.testfacepass.importmanager;

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

import java.io.File;
import java.util.Arrays;
import java.util.List;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.types.FacePassAddFaceResult;
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
    // view
    private Button mAddFace;
    private Button mRegister;
    private Button mGet;
    private Button mDelete;
    private Button mButtonImport;
    private RelativeLayout mRelativeContent;    // 显示说明的布局
    private RelativeLayout mRelativeImport;     // 显示进度的布局
    private RelativeLayout mRelativeFinish;     // 显示结果的布局

    // import
    private ProgressBar mProgressBar;
    private TextView mTextImportFinish;   // 已处理
    private TextView mTextImportSuccess;  // 成功
    private TextView mTextImportFailure;  // 失败

    // finish
    private TextView mTextFinish;           // 已处理
    private TextView mTextFinishSuccess;   // 成功
    private TextView mTextFinishFailure;   // 失败

    private Context mContext;
    private volatile boolean mImporting;

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
        mButtonImport = (Button)findViewById(R.id.button_import);
        mButtonImport.setOnClickListener(this);
        mRelativeContent = (RelativeLayout) findViewById(R.id.relative_content);
        mRelativeImport = (RelativeLayout)findViewById(R.id.relative_progress);
        mRelativeFinish = (RelativeLayout)findViewById(R.id.relative_finish);
        mProgressBar = (ProgressBar)findViewById(R.id.progress_bar);
        mTextImportFinish = (TextView)findViewById(R.id.text_import_finish);
        mTextImportSuccess = (TextView)findViewById(R.id.text_import_success);
        mTextImportFailure = (TextView)findViewById(R.id.text_import_failure);
        mTextFinish = (TextView)findViewById(R.id.text_finish);
        mTextFinishSuccess = (TextView)findViewById(R.id.text_finish_success);
        mTextFinishFailure = (TextView)findViewById(R.id.text_finish_failure);
    }

    private void initData() {
        ImportFileManager.getInstance().setOnImportListener(this);
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
            case R.id.button_import_back:
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

    /**
     * 导入结束，显示导入结果
     */
    @Override
    public void endImport(final int finishCount, final int successCount, final int failureCount) {
        // 数据变化，更新内存
//        FaceApi.getInstance().initDatabases(true);
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
            for (int i = 0; i < picFiles.length; i++) {
                // 获取图片名
                String picName = picFiles[i].getName();
                // 判断图片后缀
                if (!picName.endsWith(".jpg") && !picName.endsWith(".png")) {
                    LogUtils.i(TAG, "图片后缀不满足要求");
                    continue;
                }
                //判断文件名是否符合规则 文件名中是否有"facepass-"
                if (-1 != picName.indexOf(GROUP_NAME + DELIMITER)) {
                    File imageFile = picFiles[i];
                    if (!imageFile.exists()) {
                        toast("图片不存在！path:" + imageFile.getPath() + ",name:" + imageFile.getName());
                        break;
                    }
                    String imagePath = imageFile.getPath();
                    Log.i(TAG, "imagePath:" + imagePath);
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                    if (null != bitmap) {
                        try {
                            android.util.Log.d("addface", "addFace");
                            FacePassAddFaceResult result = facePassHandler.addFace(bitmap);  //人脸图片入库
                            android.util.Log.d("addface", "result:" + result
                                    + ",bl:" + result.blur
                                    + ",pp:" + result.pose.pitch
                                    + ",pr:" + result.pose.roll
                                    + ",py" + result.pose.yaw);
                            if (result != null) {
                                if (result.result == 0) {  //入库成功
                                    String faceToken = new String(result.faceToken);
                                    boolean isSuccess = false;
                                    String bindStr = "";
                                    try {
                                        isSuccess = facePassHandler.bindGroup(GROUP_NAME, result.faceToken);
                                        bindStr = isSuccess ? "success" : "failed";
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        toast(e.getMessage());
                                    }
                                    toast("add face successfully！" + result.faceToken + ",bind " + bindStr + "!");
                                    if (isSuccess) {  //绑定底库成功
                                        // 保存图片到新目录中
                                        File facePicDir = FileUtils.getBatchImportSuccessDirectory();
                                        if (facePicDir != null) {
                                            File savePicPath = new File(facePicDir, picName);
                                            if (FileUtils.saveBitmap(savePicPath, bitmap)) {
                                                LogUtils.i(TAG, "图片保存成功");
                                            } else {
                                                LogUtils.i(TAG, "图片保存失败");
                                            }
                                        }
                                    }
                                } else if (result.result == 1) {
                                    toast("no face ！");
                                } else {
                                    toast("quality problem！");
                                }
                            }
                            Log.e("addface", "ret:" + result.result + ",blur:" + result.blur + ",brightness:" + result.brightness + ",deviation:" + result.deviation + ",pitch:" + result.pose.pitch
                                    + ",roll:" + result.pose.roll + ",yaw:" + result.pose.yaw + ",left:" + result.facePassRect.left + ",right:" + result.facePassRect.right
                                    + ",top:" + result.facePassRect.top + ",bottom:" + result.facePassRect.bottom);
                            int width = result.facePassRect.right - result.facePassRect.left;
                            int height = result.facePassRect.bottom - result.facePassRect.top;
                            Log.e("addface", "face:" + width + "x" + height);
                        } catch (FacePassException e) {
                            e.printStackTrace();
                            toast(e.getMessage());
                        }

                        // 图片回收
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }

                }
            }
        }
        toast("add face finish！");
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
    private void toast(String msg) {
        Toast.makeText(BatchImportActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
    //删除底库
//                    LogUtils.i(TAG, "clearAllGroupsAndFaces begin");
//                    MainActivity.mFacePassHandler.clearAllGroupsAndFaces();
//                    LogUtils.i(TAG, "clearAllGroupsAndFaces end");
}
