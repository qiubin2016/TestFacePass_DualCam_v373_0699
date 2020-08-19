package megvii.testfacepass.custom.db

import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import megvii.testfacepass.custom.importmanager.LogUtils

class DbOpt {
    companion object {
        val TAG: String? = DbOpt::class.simpleName
        fun insert() {
            val userBox: Box<UserInfo> = ObjectBox.boxStore.boxFor()
            //user_name,group,phone_num,user_id,build,unit,room,status,face_token
            val userInfo = UserInfo(0, "qb", "facepass", "13300000001", "001", "1", "1", "101",
                    "1", "192010291")
            userBox.put(userInfo)
            val userCount = userBox.count()
            LogUtils.d(TAG, "count:$userCount")
        }
        fun delete() {
            val userBox: Box<UserInfo> = ObjectBox.boxStore.boxFor()
            userBox.removeAll()
            LogUtils.d(TAG, "remove all")
        }
        fun update() {

        }
        fun query() {
            val userBox: Box<UserInfo> = ObjectBox.boxStore.boxFor()
            val results = userBox.query().build().find()
            LogUtils.d(TAG, "results:$results")
        }

    }

    fun insert() {
//        val userBox: Box<UserInfo> = ObjectBox.boxStore.boxFor()
    }
}