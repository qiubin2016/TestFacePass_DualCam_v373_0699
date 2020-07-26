package megvii.testfacepass.importmanager;

import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import org.json.JSONException;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static megvii.testfacepass.importmanager.APIUtil.JsonToMap;
import static megvii.testfacepass.importmanager.APIUtil.aesDecrypt;
import static megvii.testfacepass.importmanager.APIUtil.aesEncryptSDK;
import static megvii.testfacepass.importmanager.APIUtil.getSDKV2Md5;

public class UserManager {
    private static final String TAG = UserManager.class.getSimpleName();
    private static final String REQUEST_URL = "http://sdk.api.jia-r.com/sdk/cloud/base/developerLogin";
    private static final String REQUEST_URL_TEST = "http://test.sdk.api.jia-r.com/sdk/cloud/base/developerLogin";
    private static final String APP_ID = "978887645289676800";
    private static final String APP_SECRET = "5E6C5A3B5A720340";
    private static final String PROJECT_ID = "00000060";
    public static String getLoginInput() {
        String encryptResult = "";
        String decryptResult;

        String projectId = PROJECT_ID;
        String timestamp = new Date().getTime() + "";
        JSONObject json = new JSONObject();
        JSONObject protocol = new JSONObject();
        JSONObject client = new JSONObject();
        JSONObject param = new JSONObject();
        String requestId = UUID.randomUUID().toString().replaceAll("-", "");  //"42e9da0ccd5e4fd4b479560debbd71a7";
        try {
            json.put("requestId", requestId);
            protocol.put("version", "2.0");
            client.put("appSecret", APP_SECRET);
            client.put("projectId", projectId);
            json.put("protocol", protocol);
            json.put("client", client);
            json.put("param", param);
            json.put("timestamp", timestamp);

            Map<String, Object> jsonToMap;//将json转换为map
            jsonToMap = JsonToMap (protocol.toString());
            jsonToMap.putAll(JsonToMap (client.toString()));
            jsonToMap.putAll(JsonToMap (param.toString()));
            jsonToMap.remove("appSecret");
            String sign = getSDKV2Md5(jsonToMap, requestId, timestamp, APP_SECRET);//签名运算
            json.put("sign", sign);
            Log.i(TAG, "json:" + json.toJSONString());
            encryptResult = aesEncryptSDK(json.toJSONString(), APP_SECRET);//使用key进行aes加密运算
            Log.i(TAG, "加密后的入参:"+ encryptResult);
            //解密入参
            decryptResult = aesDecrypt(encryptResult, APP_SECRET);
            Log.i(TAG,"入参加密前:"+ decryptResult);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encryptResult;
    }

    public static void login() {
        String encryptResult = getLoginInput();  //入参处理
        Log.i(TAG, "encryptResult:" + encryptResult);
        String encrypScript = Newexample.sendPost(REQUEST_URL_TEST,
                "appId=" + APP_ID + "&encryptScript=" + encryptResult.replaceAll("\\+","%2B"));
        JSONObject json_test = (JSONObject) JSONObject.parse(encrypScript);
        Log.i(TAG, "encryptSrcipt:" + json_test. get("encryptScript").toString());
        try {
            encrypScript = aesDecrypt(json_test.get("encryptScript").toString(), APP_SECRET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("出参解密后:" + encrypScript);
    }
    public static void register() {
        String appSecret = "5E6C5A3B5A720340";
        String projectId = "00000060";
        String timestamp = new Date().getTime() + "";
        JSONObject json = new JSONObject();
        JSONObject protocol = new JSONObject();
        JSONObject client = new JSONObject();
        JSONObject param = new JSONObject();
        String requestId = UUID.randomUUID().toString().replaceAll("-", "");
        try {
            json.put("requestId", requestId);
            protocol.put("version", "2.0");
            client.put("appSecret", appSecret);
            client.put("projectId", projectId);
            json.put("protocol", protocol);
            json.put("client", client);
            json.put("param", param);
            json.put("timestamp", timestamp);
//            JSON.parse
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
