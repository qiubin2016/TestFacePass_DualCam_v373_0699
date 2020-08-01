package megvii.testfacepass.custom.importmanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.json.JSONException;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static megvii.testfacepass.custom.importmanager.APIUtil.JsonToMap;
import static megvii.testfacepass.custom.importmanager.APIUtil.aesDecrypt;
import static megvii.testfacepass.custom.importmanager.APIUtil.aesEncryptSDK;
import static megvii.testfacepass.custom.importmanager.APIUtil.getSDKV2Md5;

public class UserManager {
    private static final String TAG = UserManager.class.getSimpleName();
    private static final String REQUEST_URL = "http://sdk.api.jia-r.com";
    private static final String REQUEST_URL_TEST = "http://test.sdk.api.jia-r.com";
    private static final String URL_LOGIN = "/sdk/cloud/base/developerLogin";
    private static final String URL_GETBUILDLIST = "/sdk/cloud/base/getBuildList";
    private static final String URL_GETUNITLIST = "/sdk/cloud/base/getUnitList";
    private static final String URL_GETROOMLIST = "/sdk/cloud/base/getRoomList";
    private static final String URL_ADDUSERBINDROOM = "/sdk/cloud/face/addUserBindRoom";

    private static final String APP_ID = "978887645289676800";
    private static final String APP_SECRET = "5E6C5A3B5A720340";
    private static final String PROJECT_ID = "00000060";
    private static String mToken = "";
    private static String mBuildId = "";
    private static String mUnitId = "";
    private static String mRoomId = "";
    public static String getInputParam(String appSecret, JSONObject param) {
        String encryptResult = "";
        String decryptResult;

        String projectId = PROJECT_ID;
        String timestamp = new Date().getTime() + "";
        JSONObject json = new JSONObject();
        JSONObject protocol = new JSONObject();
        JSONObject client = new JSONObject();
        JSONObject paramObj;
        String requestId = UUID.randomUUID().toString().replaceAll("-", "");  //"42e9da0ccd5e4fd4b479560debbd71a7";
        try {
            json.put("requestId", requestId);
            protocol.put("version", "2.0");
            if ((null == appSecret) || (appSecret.isEmpty())) {  //appSecret和projectId互斥
                client.put("projectId", projectId);
            } else {
                client.put("appSecret", APP_SECRET);
            }
            json.put("protocol", protocol);
            json.put("client", client);
            if (null != param) {
                paramObj = param;
            } else {
                paramObj = new JSONObject();
            }
            json.put("param", paramObj);
            json.put("timestamp", timestamp);

            Map<String, Object> jsonToMap;//将json转换为map
            jsonToMap = JsonToMap (protocol.toString());
            jsonToMap.putAll(JsonToMap (client.toString()));
            jsonToMap.putAll(JsonToMap (paramObj.toString()));
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

    /**
     * {"msgCode":0,"msg":"成功","requestId":"d95a48f764074d22b244dea0482044e7","timestamp":"1595742748112","sign":"dce155276195e6c580ce12f807e778f6","data":{"token":"ORW73TTUasPSkwlVDgR3Kys+kSKoR8b6yVPb9ECBl20JUqDJjNERoKRnR4ktUeKnAITlPiKHvDftBvU9hcoSEg=="}}
     * {
     *     "msgCode":0,
     *     "msg":"成功",
     *     "requestId":"d95a48f764074d22b244dea0482044e7",
     *     "timestamp":"1595742748112",
     *     "sign":"dce155276195e6c580ce12f807e778f6",
     *     "data":{
     *         "token":"ORW73TTUasPSkwlVDgR3Kys+kSKoR8b6yVPb9ECBl20JUqDJjNERoKRnR4ktUeKnAITlPiKHvDftBvU9hcoSEg=="
     *     }
     * }
     */
    public static void login() {
        String result;
        Log.i(TAG, "=====================login");
        result = postToServerLogin();  //向后台登录
        JSONObject object = JSONObject.parseObject(result);
        Log.i(TAG, "==========");
        if (null != object) {
            int msgCode = object.getInteger("msgCode");
            Log.i(TAG, "msgCode:" + msgCode);
            if (0 == msgCode) {  //成功
                String dataStr = object.getString("data");
                Log.i(TAG, "data:" + dataStr);
                JSONObject dataObj = JSONObject.parseObject(dataStr);
                if (null != dataObj) {
                    mToken = dataObj.getString("token");
                    Log.i(TAG, "token:" + mToken);
                    mToken = mToken.replaceAll("\\+", "%2B");
                    Log.i(TAG, "token:" + mToken);
                }
            }
        }
    }
    /**
     * deviceType	String	Y	固定为7
     * buildNum	String	N	楼栋编号（3位数字）
     * unitNum	String	N	单元编号（2位数字）
     * roomNum	String	N	房间编号（2位层号+2位房号），同一个单元中房间编号不可重复
     * currentPage	String	N	当前页默认为1
     * pageSize	String	N	页面大小默认为15
     */
    public static JSONObject getBuildUnitRoomParam(String buildNum, String unitNum, String roomNum, String currentPage, String pageSize) {
        JSONObject obj = new JSONObject();
        obj.put("deviceType", 7);
        if ((null != buildNum) && (!buildNum.isEmpty())) {
            obj.put("buildNum", buildNum);
        }
        if ((null != unitNum) && (!unitNum.isEmpty())) {
            obj.put("unitNum", unitNum);
        }
        if ((null != roomNum) && (!roomNum.isEmpty())) {
            obj.put("roomNum", roomNum);
        }
        obj.put("currentPage", currentPage);
        obj.put("pageSize", pageSize);
        Log.i(TAG, "obj:" + obj.toString());

        return obj;
    }
    public static String postToServerLogin() {
        Log.i(TAG, "=====================postToServerLogin");
        String encryptResult = getInputParam(APP_SECRET, null);  //入参处理
        Log.i(TAG, "encryptResult:" + encryptResult);
        String encrypScript = Newexample.sendPost(REQUEST_URL_TEST + URL_LOGIN,
                "appId=" + APP_ID + "&encryptScript=" + encryptResult.replaceAll("\\+","%2B"));
        JSONObject json_test = (JSONObject) JSONObject.parse(encrypScript);
        Log.i(TAG, "encryptSrcipt:" + json_test. get("encryptScript").toString());
        try {
            encrypScript = aesDecrypt(json_test.get("encryptScript").toString(), APP_SECRET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "出参解密后:" + encrypScript);
        return encrypScript;
    }
    public static String postToServer(String url, String appSecret, JSONObject param) {
        Log.i(TAG, "=====================postToServer");
        Log.i(TAG, "=====================postToServer001");
        Log.i(TAG, "=====================postToServer002");
        if (mToken.isEmpty()) {
            Log.i(TAG, "token is empty");
            login();  //登录获取token
        }
        Log.i(TAG, "=====================postToServer1");
        String encryptResult = getInputParam(null, param);  //入参处理
        Log.i(TAG, "encryptResult:" + encryptResult);
        String encrypScript = Newexample.sendPost(url,
                "token=" + mToken + "&encryptScript=" + encryptResult.replaceAll("\\+","%2B"));
        JSONObject json_test = (JSONObject) JSONObject.parse(encrypScript);
        Log.i(TAG, "encryptSrcipt:" + json_test. get("encryptScript").toString());
        try {
            encrypScript = aesDecrypt(json_test.get("encryptScript").toString(), APP_SECRET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "出参解密后:" + encrypScript);
        return encrypScript;
    }
    /**
     * deviceType	String	Y	固定为7
     * buildNum	String	N	楼栋编号（3位数字）
     * currentPage	String	N	当前页默认为1
     * pageSize	String	N	页面大小默认为15
     */
    public static void getBuildList() {
        String result;
        result = postToServer(REQUEST_URL_TEST + URL_GETBUILDLIST,
                null,
                getBuildUnitRoomParam("001", null, null, "1", "15"));
        if ((null != result) && (!result.isEmpty())) {
            JSONObject object = JSONObject.parseObject(result);
            if (null != object) {
                int msgCode = object.getInteger("msgCode");
                Log.i(TAG, "msgCode:" + msgCode);
                if (0 == msgCode) {  //成功
                    JSONObject dataObj = object.getJSONObject("data");
                    Log.i(TAG, "data:" + dataObj.toString());
                    JSONArray rowsArr = dataObj.getJSONArray("rows");
                    Log.i(TAG, "rows:" + rowsArr.toString());
                    for (Iterator itr = rowsArr.iterator(); itr.hasNext();) {
                        JSONObject jsonObj = (JSONObject) itr.next();
                        mBuildId = jsonObj.getString("buildId");
                        Log.i(TAG, "buildId:" + mBuildId);
                        break;  //只取第1个数组元素
                    }
                }
            }
        }
    }
    /**
     * deviceType	String	Y	固定为7
     * buildNum	String	Y	楼栋编号（3位数字）
     * unitNum	String	Y	单元编号（2位数字）
     * currentPage	String	N	当前页默认为1
     * pageSize	String	N	页面大小默认为15
     */
    public static void getUnitList() {
        String result;
        result = postToServer(REQUEST_URL_TEST + URL_GETUNITLIST,
                null,
                getBuildUnitRoomParam("001", "01", null, "1", "15"));
        if ((null != result) && (!result.isEmpty())) {
            JSONObject object = JSONObject.parseObject(result);
            if (null != object) {
                int msgCode = object.getInteger("msgCode");
                Log.i(TAG, "msgCode:" + msgCode);
                if (0 == msgCode) {  //成功
                    JSONObject dataObj = object.getJSONObject("data");
                    Log.i(TAG, "data:" + dataObj.toString());
                    JSONArray rowsArr = dataObj.getJSONArray("rows");
                    Log.i(TAG, "rows:" + rowsArr.toString());
                    for (Iterator itr = rowsArr.iterator(); itr.hasNext();) {
                        JSONObject jsonObj = (JSONObject) itr.next();
                        mUnitId = jsonObj.getString("unitId");
                        Log.i(TAG, "unitId:" + mUnitId);
                        break;  //只取第1个数组元素
                    }
                }
            }
        }
    }
    /**
     * {"msgCode":0,"msg":"成功","requestId":"8d9c1e2a9fda4cfc971888dc8216e663","timestamp":"1595763372166","sign":"e67ef78c7e7787afc14e446765c2f137","data":{"page":{"currentPage":1,"pageSize":15,"startSlip":0,"totalPage":1,"totalNumber":1,"skipCount":0},"rows":[{"roomNum":"0204","address":"南山智园C11单元","roomId":"880352406444081152","liftDoorType":0,"roomType":1}]}}
     * {
     *     "msgCode":0,
     *     "msg":"成功",
     *     "requestId":"8d9c1e2a9fda4cfc971888dc8216e663",
     *     "timestamp":"1595763372166",
     *     "sign":"e67ef78c7e7787afc14e446765c2f137",
     *     "data":{
     *         "page":{
     *             "currentPage":1,
     *             "pageSize":15,
     *             "startSlip":0,
     *             "totalPage":1,
     *             "totalNumber":1,
     *             "skipCount":0
     *         },
     *         "rows":[
     *             {
     *                 "roomNum":"0204",
     *                 "address":"南山智园C11单元",
     *                 "roomId":"880352406444081152",
     *                 "liftDoorType":0,
     *                 "roomType":1
     *             }
     *         ]
     *     }
     * }
     */
    public static void getRoomList() {
        String result;
        result = postToServer(REQUEST_URL_TEST + URL_GETROOMLIST,
                null,
                getBuildUnitRoomParam("001", "01", "0204", "1", "15"));
        if ((null != result) && (!result.isEmpty())) {
            JSONObject object = JSONObject.parseObject(result);
            if (null != object) {
                int msgCode = object.getInteger("msgCode");
                Log.i(TAG, "msgCode:" + msgCode);
                if (0 == msgCode) {  //成功
                    JSONObject dataObj = object.getJSONObject("data");
                    Log.i(TAG, "data:" + dataObj.toString());
                    JSONArray rowsArr = dataObj.getJSONArray("rows");
                    Log.i(TAG, "rows:" + rowsArr.toString());
                    for (Iterator itr = rowsArr.iterator(); itr.hasNext();) {
                        JSONObject jsonObj = (JSONObject) itr.next();
                        mRoomId = jsonObj.getString("roomId");
                        Log.i(TAG, "roomId:" + mRoomId);
                        break;  //只取第1个数组元素
                    }
                }
            }
        }
    }
    /**
     * phoneNum	String	Y	手机号
     * realName	String	Y	姓名
     * gender	Integer	Y	性别：1男 0女
     * buildId	String	Y	楼栋Id
     * unitId	String	Y	单元Id
     * roomId	String	Y	房间Id
     * roomNum	String	Y	房间号
     * houserType	Integer	Y	住户身份：1业主本人 2亲属 3租客
     * imageStr	String	Y	图片信息base64
     */
    public static JSONObject getAddUserBindRoomParam(String phoneNum, String name, int gender, String buildId, String unitId, String roomId,
                                                     String roomNum, int houserType, String image) {
        JSONObject obj = new JSONObject();
        obj.put("phoneNum", phoneNum);
        obj.put("realName", name);
        obj.put("gender", gender);
        obj.put("buildId", buildId);
        obj.put("unitId", unitId);
        obj.put("roomId", roomId);
        obj.put("roomNum", roomNum);
        obj.put("houserType", houserType);
        obj.put("imageStr", image);

        return obj;
    }
    public static void addUserBindRoom() {
        String image = "";
        byte[] bytes;
        Bitmap bitmap = BitmapFactory.decodeFile("/sdcard/Success-Import/2020071601394600018-facepass.jpg");
        if (null != bitmap) {
//            bytes = ImageUtils.bitmapToNv21(bitmap, bitmap.getWidth(), bitmap.getHeight());
            bytes = ImageUtils.Bitmap2Bytes(bitmap);
            try {
                image = APIUtil.encodeBase64(bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String result;
        result = postToServer(REQUEST_URL_TEST + URL_ADDUSERBINDROOM,
                null,
                getAddUserBindRoomParam("13300000003", "qb1", 1, mBuildId, mUnitId, mRoomId, "0204",
                        2, image));
        if ((null != result) && (!result.isEmpty())) {
            JSONObject object = JSONObject.parseObject(result);
            if (null != object) {
                int msgCode = object.getInteger("msgCode");
                Log.i(TAG, "msgCode:" + msgCode);
                if (0 == msgCode) {  //成功
                    JSONObject dataObj = object.getJSONObject("data");
                    Log.i(TAG, "data:" + dataObj.toString());
                    JSONArray rowsArr = dataObj.getJSONArray("rows");
                    Log.i(TAG, "rows:" + rowsArr.toString());
                    for (Iterator itr = rowsArr.iterator(); itr.hasNext();) {
                        JSONObject jsonObj = (JSONObject) itr.next();
                        mRoomId = jsonObj.getString("roomId");
                        Log.i(TAG, "roomId:" + mRoomId);
                        break;  //只取第1个数组元素
                    }
                }
            }
        }
    }
}
