package com.apm29.ws.websocketserver.controller.ws

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.IOException


@RestController
class Controller(@field:Autowired var webSocketServer: WebSocketServer) {

    /**
     * 群发消息内容
     *
     * @param message
     * @return
     */
    @RequestMapping(value = ["/ws/sendAll"], method = [RequestMethod.GET])
    fun sendAllMessage(@RequestParam(required = true) message: String?): String {
        try {
            webSocketServer.broadCastInfo(message)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "success"
    }

    /**
     * 指定会话ID发消息
     *
     * @param message 消息内容
     * @param userId      连接会话ID
     * @return
     */
    @RequestMapping(value = ["/ws/sendOne"], method = [RequestMethod.GET])
    fun sendOneMessage(@RequestParam(required = true) message: String?,
                       @RequestParam(required = true) userId: String?): String {
        try {
            webSocketServer.sendToUser(userId, message)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "success"
    }
}