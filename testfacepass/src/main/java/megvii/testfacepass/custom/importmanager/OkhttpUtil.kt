package megvii.testfacepass.custom.importmanager

import okhttp3.MediaType
import okhttp3.OkHttpClient

class OkhttpUtil {

    companion object {
        //编码集
        val FORM_CONTENT_TYPE: MediaType? = MediaType.parse("application/json;charset=utf-8")
        //接口地址
        const val URI:String = "http://sdk.api.jia-r.com/sdk/cloud/base/developerLogin"

        fun post() {
            //创建实例
            var client: OkHttpClient = OkHttpClient.Builder().build()
            //创建标单及数据
            var map: HashMap<String, String> = HashMap<String, String>()
//            map.put()
        }
    }

}