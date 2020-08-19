package megvii.testfacepass.custom.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * 数据库字段：user_name,group,phone_num,user_id,build,unit,room,status,face_token
 */
@Entity
data class UserInfo (
        @Id var id: Long = 0,
        var user_name: String ?= null,
        var group: String ?= null,
        var phone_num: String ?= null,
        var user_id: String ?= null,
        var build: String ?= null,
        var unit: String ?= null,
        var room: String ?= null,
        var status: String ?= null,
        var face_token: String ?= null
)