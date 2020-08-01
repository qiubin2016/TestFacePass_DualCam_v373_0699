package megvii.testfacepass.custom.importmanager;

import android.util.Base64;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class APIUtil {
    private static final String TAG = APIUtil.class.getSimpleName();
    /**
     * json转map
     *
     * @param json
     * @return map
     */
    public static Map<String, Object> JsonToMap(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json != null) {
            map = (Map) JSON.parse(json);
        }
        return map;
    }

    /**
     * <desc>
     *     云设备计算MD5
     * </desc>
     * @param params
     * @param appsecret
     * @return
     */

    public static String getSDKV2Md5(Map<String, Object> params, String requestId,String timestamp,String appsecret) {
        if (params == null || appsecret.isEmpty()) {
            return null;
        }
        StringBuffer stringA = new StringBuffer();
        List<Map.Entry<String, Object>> mappingList = new ArrayList<Map.Entry<String, Object>>(params.entrySet());
        Collections.sort(mappingList, new Comparator<Map.Entry<String, Object>>() {
            @Override
            public int compare(Map.Entry<String, Object> mappingF, Map.Entry<String, Object> mappingS) {
                return mappingF.getKey().toLowerCase().compareTo(mappingS.getKey().toLowerCase());
            }
        });
        for (Map.Entry<String, Object> mapping : mappingList) {
            stringA.append(mapping.getValue());
        }
        stringA.append(requestId);
        stringA.append(timestamp);
        stringA.append(appsecret);
        System.out.println(stringA);
        return md5(stringA.toString());
    }

    public static String md5(String str) {
        /**return DigestUtils.md5Hex(str);  //会报错java.lang.NoSuchMethodError
         * https://blog.csdn.net/piglite/article/details/50813468
         */
//        return DigestUtils.md5Hex(str);
        return new String(Hex.encodeHex(DigestUtils.md5(str)));
    }

    /**
     * <desc>
     *     云端设备AES加密方案
     * </desc>
     * @param sSrc
     * @param sKey
     * @return
     * @throws Exception
     */
    public static String aesEncryptSDK(String sSrc, String sKey) throws Exception {
        if (sKey == null) {
            System.out.print("Key为空null");
            return null;
        }
// 判断Key是否为16位
        if (sKey.length() != 16) {
            System.out.print("Key长度不是16位");
            return null;
        }
        byte[] raw = sKey.getBytes("utf-8");
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(sSrc.getBytes("utf-8"));

        StringBuilder stringBuilder = new StringBuilder();
        if (encrypted == null || encrypted.length <= 0) {
            return null;
        }
        for (int i = 0; i < encrypted.length; i++) {
            int v = encrypted[i] &0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() <2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        Log.i(TAG, "len:" + encrypted.length);
        String base64Str = encodeBase64(encrypted);//此处使用BASE64做转码功能，同时能起到2次加密的作用。
        return base64Str;
    }

    /**
     * 编码
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static String encodeBase64(byte[] input) throws Exception {
//        Class clazz = Class
//                .forName("com.sun.org.apache.xerces.internal.impl.dv.util.Base64");
//        Method mainMethod = clazz.getMethod("encode", byte[].class);
//        mainMethod.setAccessible(true);
//        Object retObj = mainMethod.invoke(null, new Object[] { input });
//        return (String) retObj;
        //base64编码
        String strBase64 = Base64.encodeToString(input,  Base64.NO_WRAP);
        return strBase64;
    }
    /**
     * 解码
     *
     * @param input
     * @return
     * @throws Exception
     */
    public static byte[] decodeBase64(String input) throws Exception {
//        Class clazz = Class
//                .forName("com.sun.org.apache.xerces.internal.impl.dv.util.Base64");
//        Method mainMethod = clazz.getMethod("decode", String.class);
//        mainMethod.setAccessible(true);
//        Object retObj = mainMethod.invoke(null, input);
//        return (byte[]) retObj;
        //base64解码
        return Base64.decode(input.getBytes(), Base64.NO_WRAP);
    }

    /**
     * <desc>
     *     云端设备AES解密
     * </desc>
     * @param sSrc
     * @param sKey
     * @return
     * @throws Exception
     */
    public static String aesDecrypt(String sSrc, String sKey) throws Exception {
        try {
// 判断Key是否正确
            if (sKey == null) {
                System.out.print("Key为空null");
                return null;
            }
// 判断Key是否为16位
            if (sKey.length() != 16) {
                System.out.print("Key长度不是16位");
                return null;
            }
            if (sSrc.length() <32) {
                System.out.print("密文串错误");
                return null;
            }
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = decodeBase64(sSrc);//先用base64解密
            Log.i(TAG, "encrypted1:" + encrypted1.length);
            try {
                byte[] original = cipher.doFinal(encrypted1);
                String originalString = new String(original, "utf-8");
                return originalString;
            } catch (Exception e) {
                System.out.println(e.toString());
                return null;
            }
        } catch (Exception ex) {
            System.out.println(ex.toString());
            return null;
        }
    }


}
