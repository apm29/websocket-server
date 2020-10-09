package com.apm29.ws.websocketserver.core

class BaseResp<T>(
        val code:Int = 200,
        val msg:String = "success",
        val data:T? = null
)