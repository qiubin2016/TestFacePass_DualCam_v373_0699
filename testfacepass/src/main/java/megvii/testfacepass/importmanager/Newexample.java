package megvii.testfacepass.importmanager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang.StringUtils;

/**
 * <desc> 旺龙云电梯sdk加密示例. </desc>
 * 
 * @createDate 2019/01/15
 */

public class Newexample {
	public static void main(String[] args) throws Exception {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		Date date = new Date();
		String createTime = df.format(date);// 签名用时间
		String appsecret = "1B4A7C7D8A9D3587";// 加密key

		takeInputPama(createTime, appsecret);// 入参处理
		takeOutPama(appsecret);// 出参处理
	}

	/**
	 * <desc> 获取加密入参 <desc/>
	 * 
	 * @param createTime
	 *            创建时间
	 * @param appsecret
	 *            开发者密码
	 * @return 加密字符串
	 * @author ITLONG
	 * @createDate 2019/01/15
	 */
	public static String takeInputPama(String createTime, String appsecret)
			throws Exception {
		JSONObject json = new JSONObject();
		json.put("projectId", "00000124");

		json.put("deviceUnique","0000012420038");
		json.put("doorDirection","1"); //0背门，1正门
		json.put("floor","1");//真实楼层，例：1  2  3  4

		Map<String, Object> jsonToMap = JsonToMap(json.toJSONString());// 将json转换为map
		String sign = cloudDeviceMd5(jsonToMap, createTime, appsecret);// 签名运算
		// 加密前先将createTime传入加密方法,最后在将createTime和sign放入json中
		json.put("createTime", createTime);
		json.put("appSecret", appsecret);
		json.put("sign", sign);
		String encryptResult = aesEncryptSDK(json.toJSONString(), appsecret);// 使用key进行aes加密运算
		// 解密入参
		String decryptResult = aesDecrypt(encryptResult, appsecret);
		System.out.println("入参加密前:" + decryptResult);
		
		System.out.println("加密后的入参:" + encryptResult);
//		encryptResult = encryptResult.replaceAll("\\+", "%2B");
		String result = sendPost("http://test.sdk.api.jia-r.com/cloud/entrance/netOpenDoor","appId=810656716076253184&encryptScript="+encryptResult);
//		
		JSONObject json1 = JSONObject.parseObject(result);
		Newexample.takeOutPama(appsecret);
		System.out.println(aesDecrypt(json1.getString("encryptScript"), appsecret));
		return encryptResult;
	}

	/**
	 * <desc> 解密出参 <desc/>
	 * 
	 * @param appsecret
	 *            开发者密码
	 * @return 加密字符串
	 * @author ITLONG
	 * @createDate 2019/01/15
	 */

	public static String takeOutPama(String appsecret) throws Exception {
		// 返回值加密串
		String encrypScript = "GUCNIx3j6lD9ZiJQlWf76KGDsUtJzPWAAJ6U6KaEm5noZMQ8+WVnQ22m2rqa7dq1";
		encrypScript = aesDecrypt(encrypScript, appsecret);
		System.out.println("出参解密后:" + encrypScript);
		return encrypScript;
	}

	public static String sendPost(String url, String param) {
		PrintWriter out = null;
		BufferedReader in = null;
		String result = "";
		try {
			URL realUrl = new URL(url);
			// 打开和URL之间的连接
			URLConnection conn = realUrl.openConnection();
			// 设置通用的请求属性
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent","Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			// 发送POST请求必须设置如下两行
			conn.setDoOutput(true);
			conn.setDoInput(true);
			//1.获取URLConnection对象对应的输出流
			out = new PrintWriter(conn.getOutputStream());
			//2.中文有乱码的需要将PrintWriter改为如下
			//out=new OutputStreamWriter(conn.getOutputStream(),"UTF-8")
			// 发送请求参数
			out.print(param);
			// flush输出流的缓冲
			out.flush();
			// 定义BufferedReader输入流来读取URL的响应
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("发送 POST 请求出现异常！"+e);
			e.printStackTrace();
		}
		//使用finally块来关闭输出流、输入流
		finally{
			try{
				if(out!=null){
					out.close();
				}
				if(in!=null){
					in.close();
				}
			}
			catch(IOException ex){
				ex.printStackTrace();
			}
		}
		System.out.println("post推送结果："+result);
		return result;
	}


	/**
	 * <desc> 云设备计算MD5 </desc>
	 * 
	 * @param params
	 * @param appsecret
	 * @return
	 */

	public static String cloudDeviceMd5(Map<String, Object> params,
                                        String createTime, String appsecret) {
		if (params == null || StringUtils.isBlank(appsecret)) {
			return null;
		}
		StringBuffer stringA = new StringBuffer();
		List<Map.Entry<String, Object>> mappingList = new ArrayList<Map.Entry<String, Object>>(
				params.entrySet());
		Collections.sort(mappingList,
				new Comparator<Map.Entry<String, Object>>() {
					@Override
					public int compare(Map.Entry<String, Object> mappingF,
                                       Map.Entry<String, Object> mappingS) {
						return mappingF.getKey().toLowerCase()
								.compareTo(mappingS.getKey().toLowerCase());
					}
				});
		for (Map.Entry<String, Object> mapping : mappingList) {
			stringA.append(mapping.getValue());
		}
		stringA.append(createTime);
		stringA.append(appsecret);
		System.out.println("签名前：" + stringA);
		return md5(stringA.toString());
	}

	public static String md5(String str) {
		return DigestUtils.md5Hex(str);
	}

	/**
	 * <desc> 云端设备AES加密方案 </desc>
	 * 
	 * @param sSrc
	 * @param sKey
	 * @return
	 * @throws Exception
	 */
	public static String aesEncryptSDK(String sSrc, String sKey)
			throws Exception {
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
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");// "算法/模式/补码方式"
		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		byte[] encrypted = cipher.doFinal(sSrc.getBytes("utf-8"));
		StringBuilder stringBuilder = new StringBuilder();
		if (encrypted == null || encrypted.length <= 0) {
			return null;
		}
		for (int i = 0; i < encrypted.length; i++) {
			int v = encrypted[i] & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		String base64Str = encodeBase64(encrypted);// 此处使用BASE64做转码功能，同时能起到2次加密的作用。
		return base64Str;
	}

	/**
	 * <desc> 云端设备AES解密 </desc>
	 * 
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
			if (sSrc.length() < 32) {
				System.out.print("密文串错误");
				return null;
			}
			byte[] raw = sKey.getBytes("utf-8");
			SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec);
			byte[] encrypted1 = decodeBase64(sSrc);// 先用base64解密
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
	 * 编码
	 * 
	 * @param input
	 * @return
	 * @throws Exception
	 */
	public static String encodeBase64(byte[] input) throws Exception {
		Class clazz = Class
				.forName("com.sun.org.apache.xerces.internal.impl.dv.util.Base64");
		Method mainMethod = clazz.getMethod("encode", byte[].class);
		mainMethod.setAccessible(true);
		Object retObj = mainMethod.invoke(null, new Object[] { input });
		return (String) retObj;
	}

	/**
	 * 解码
	 * 
	 * @param input
	 * @return
	 * @throws Exception
	 */
	public static byte[] decodeBase64(String input) throws Exception {
		Class clazz = Class
				.forName("com.sun.org.apache.xerces.internal.impl.dv.util.Base64");
		Method mainMethod = clazz.getMethod("decode", String.class);
		mainMethod.setAccessible(true);
		Object retObj = mainMethod.invoke(null, input);
		return (byte[]) retObj;
	}

}