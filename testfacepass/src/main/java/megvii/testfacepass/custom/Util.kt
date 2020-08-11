package com.lee.zbardemo

import android.util.Log
import kotlin.experimental.and

class Util() {
    companion object {
        private val TAG: String? = Util::class.simpleName

        /**
         * "AB122F"---->byte[0]:0xAB,byte[1]:0x12,byte[3]:0x2F
         * 若输入非hex字符，将转换成错误结果
         */
        fun hexStr2ByteArray(s: String): ByteArray {
            val len = s.length
            var data = ByteArray(0)  //避免返回null，会崩溃
            var i = 0

            if (0 != (len % 2)) {  //长度不是偶数则为非法长度
                return data
            }
            data = ByteArray(len / 2)
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                        + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        fun ByteArray2HexStr(byteArray: ByteArray, length: Int, withSpaces: Boolean): String {
            val hexString = StringBuilder("")
            byteArray?.let {
                if (length > 0) {
                    for (i in 0 until length) {
                        val v = byteArray[i].toInt() and 0xFF
                        val hv = Integer.toHexString(v)
                        if (hv.length < 2) {
                            hexString.append(0)
                        }
                        hexString.append(hv)
                        if (withSpaces) {
                            hexString.append(" ")
                        }
                    }
                }
            }
            return hexString.toString().toUpperCase()
        }
        fun ByteArray2HexStr(byteArray: ByteArray, withSpaces: Boolean): String? {
            return ByteArray2HexStr(byteArray, byteArray.size, withSpaces)
        }

        fun str2Hex1(str: String): String? {
            val chars = str.toCharArray()
            val hex = StringBuffer()
            for (i in chars.indices) {
                hex.append(Integer.toHexString(chars[i].toInt()))
            }
            return hex.toString()
        }

    }
}