package com.starrow.epgtimer.data.edcb

object CtrlCmd {
    const val CMD_VER = 5

    const val CMD_EPG_SRV_DEL_RESERVE = 1014
    const val CMD_EPG_SRV_ENUM_SERVICE = 1021
    const val CMD_EPG_SRV_GET_PG_INFO = 1023
    const val CMD_EPG_SRV_SEARCH_PG = 1025
    const val CMD_EPG_SRV_ENUM_PG_INFO_EX = 1029
    const val CMD_EPG_SRV_ENUM_PG_ARC = 1030
    const val CMD_EPG_SRV_DEL_AUTO_ADD = 1033
    const val CMD_EPG_SRV_ENUM_RESERVE2 = 2011
    const val CMD_EPG_SRV_GET_RESERVE2 = 2012
    const val CMD_EPG_SRV_ADD_RESERVE2 = 2013
    const val CMD_EPG_SRV_CHG_RESERVE2 = 2015
    const val CMD_EPG_SRV_ENUM_RECINFO2 = 2017
    const val CMD_EPG_SRV_SEARCH_PG_ARC2 = 2129
    const val CMD_EPG_SRV_ENUM_AUTO_ADD2 = 2131
    const val CMD_EPG_SRV_ADD_AUTO_ADD2 = 2132
    const val CMD_EPG_SRV_CHG_AUTO_ADD2 = 2134
    const val CMD_EPG_SRV_GET_STATUS_NOTIFY2 = 2200
    const val CMD2_EPG_SRV_FILE_COPY2 = 2060
}

object ErrCode {
    const val CMD_SUCCESS = 1
    const val CMD_ERR = 0
    const val CMD_NON_SUPPORT = 203
    const val CMD_ERR_INVALID_ARG = 204
    const val CMD_ERR_CONNECT = 205
    const val CMD_ERR_DISCONNECT = 206
    const val CMD_ERR_TIMEOUT = 207
    const val CMD_ERR_BUSY = 208
    const val CMD_NO_RES = 250

    fun name(code: Int): String = when (code) {
        CMD_SUCCESS -> "成功"
        CMD_ERR -> "汎用エラー"
        CMD_NON_SUPPORT -> "未サポートのコマンド"
        CMD_ERR_INVALID_ARG -> "引数エラー"
        CMD_ERR_CONNECT -> "サーバーにコネクトできなかった"
        CMD_ERR_DISCONNECT -> "サーバーから切断された"
        CMD_ERR_TIMEOUT -> "タイムアウト発生"
        CMD_ERR_BUSY -> "ビジー状態で現在処理できない"
        CMD_NO_RES -> "コマンドの応答を保留"
        else -> "不明なエラーコード($code)"
    }
}
