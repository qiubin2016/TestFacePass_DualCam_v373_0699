package megvii.testfacepass.custom;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.dianping.logan.Logan;
import com.dianping.logan.LoganConfig;

import java.io.File;

public class CfgApp extends Application {
    private static final String TAG = CfgApp.class.getSimpleName();

    private static final String BOARD_SMDT = "rk30sdk";  //视美泰rk3288主板
    private static boolean isSmdt;

    private static final String BOARD_REDMI_6_PRO = "msm8953";
    private static boolean isRedMi6Pro;

    static {
        //获取板子信息
        if (BOARD_SMDT.equals(Build.BOARD)) {
            isSmdt = true;
        } else if (BOARD_REDMI_6_PRO.equals(Build.BOARD)) {
            isRedMi6Pro = true;
        }
        Log.i(TAG, "board is " + Build.BOARD);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //初始化logan
        LoganConfig config = new LoganConfig.Builder()
                .setCachePath(getApplicationContext().getFilesDir().getAbsolutePath())
                .setPath(getApplicationContext().getExternalFilesDir(null).getAbsolutePath()
                        + File.separator + "logan_v1")
                .setEncryptKey16("0123456789012345".getBytes())
                .setEncryptIV16("0123456789012345".getBytes())
                .build();
        Logan.init(config);
        Logan.w("------------------", 2);
        Log.e(TAG, "cache path:" + getApplicationContext().getFilesDir().getAbsolutePath());
        Log.e(TAG, "path:" + getApplicationContext().getExternalFilesDir(null).getAbsolutePath()
                + File.separator + "logan_v1");
        Log.e(TAG, Logan.getAllFilesInfo().toString());
    }

    public static boolean isSmdt() {
        return isSmdt;
    }

    public static boolean isRedMi6Pro() {
        return isRedMi6Pro;
    }
}
