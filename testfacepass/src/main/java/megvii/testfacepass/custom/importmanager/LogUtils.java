package megvii.testfacepass.custom.importmanager;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;


public class LogUtils {
    private static final int NUM_SPLIT = 3800;
    private static boolean isDebug = SingleBaseConfig.getBaseConfig().isDebug();

    public static void setIsDebug(boolean isDebug) {
        LogUtils.isDebug = isDebug;
    }

    public interface LogInterface {
        public int println(String tag, String msg) ;
    }
    public static LogInterface Logv = new LogInterface() {
        @Override
        public int println(String tag, String msg) {
            return Log.v(tag, msg);
        }
    };

    public static LogInterface Logi = new LogInterface() {
        @Override
        public int println(String tag, String msg) {
            return Log.i(tag, msg);
        }
    };

    public static LogInterface Logw = new LogInterface() {
        @Override
        public int println(String tag, String msg) {
            return Log.w(tag, msg);
        }
    };

    public static LogInterface Loge = new LogInterface() {
        @Override
        public int println(String tag, String msg) {
            return Log.e(tag, msg);
        }
    };

    public static LogInterface Logd = new LogInterface() {
        @Override
        public int println(String tag, String msg) {
            return Log.d(tag, msg);
        }
    };

    public static int print(String tag, String msg, LogInterface logInterface) {
        String str = msg;
        String tmpStr;
        int len = str.length();
        int ret = len;
        boolean first = true;
//        Log.e("print", "len:" + len);
        while (len > 0) {
            if (len >= NUM_SPLIT) {
                tmpStr = str.substring(0, NUM_SPLIT);
                str = str.substring(NUM_SPLIT, len);
                len -= NUM_SPLIT;
//                Log.e("print", "len1:" + len);
                if (first) {
                    first = false;
                    logInterface.println(tag, tmpStr);
                } else {
//                    System.out.print(tmpStr);
                    logInterface.println(tag, tmpStr);
                }
            } else if (first) {
//                Log.e("print", "len2:" + len);
                logInterface.println(tag, msg);  //不足NUM_SPLIT，直接打印
                break;
            } else {
//                Log.e("print", "len3:" + len);
//                System.out.println(str);
                logInterface.println(tag, str);
                break;
            }
        }
        return ret;
    }

    public static int v(String tag, String msg) {
        if (isDebug) {
//            return Log.v(tag, msg);
            return print(tag, msg, Logv);
        } else {
            return -1;
        }
    }
    public static int d(String tag, String msg) {
        if (isDebug) {
//            return Log.d(tag, msg);
            return print(tag, msg, Logd);
        } else {
            return -1;
        }
    }

    public static int i(String tag, String msg) {
        if (isDebug) {
//            return Log.i(tag, msg);
            return print(tag, msg, Logi);
        } else {
            return -1;
        }
    }

    public static int w(String tag, String msg) {
        if (isDebug) {
//            return Log.w(tag, msg);
            return print(tag, msg, Logw);
        } else {
            return -1;
        }
    }

    public static int e(String tag, String msg) {
        if (isDebug) {
//            return Log.e(tag, msg);
            return print(tag, msg, Loge);
        } else {
            return -1;
        }
    }
}
