package megvii.testfacepass.custom;

import android.app.Application;
import android.os.Build;
import android.util.Log;

public class CfgApp /*extends Application*/ {
    private static final String TAG = CfgApp.class.getSimpleName();

    private static final String BOARD_SMDT = "rk30sdk";  //视美泰rk3288主板
    private static boolean isSmdt;

    private static final String BOARD_REDMI_6_PRO = "msm8953";
    private static boolean isRedMi6Pro;

    static {
        if (BOARD_SMDT.equals(Build.BOARD)) {
            isSmdt = true;
        } else if (BOARD_REDMI_6_PRO.equals(Build.BOARD)) {
            isRedMi6Pro = true;
        }
        Log.i(TAG, "board is " + Build.BOARD);
    }

    public static boolean isSmdt() {
        return isSmdt;
    }

    public static boolean isRedMi6Pro() {
        return isRedMi6Pro;
    }
}
