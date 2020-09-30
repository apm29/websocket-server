package com.apm29.ws.websocketserver.controller.ws

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.websocket.*
import javax.websocket.server.PathParam
import javax.websocket.server.ServerEndpoint


/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 */

@ServerEndpoint("/websocket/{userId}")
@Component
class WebSocketServer {
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private lateinit var webSocketSession: Session

    //当前发消息的人员userId
    private lateinit var userId: String

    /**
     * 连接建立成功调用的方法 */
    @OnOpen
    fun onOpen(@PathParam(value = "userId") param: String, WebSocketsession: Session, config: EndpointConfig?) {
        this.userId = param
        //log.info("authKey:{}",authKey);
        this.webSocketSession = WebSocketsession
        webSocketSet[param] = this //加入map中
        val cnt = OnlineCount.incrementAndGet() // 在线数加1
        logger.info("有连接加入，当前连接数为：{}", cnt)
        if(cnt>2){
            webSocketSet.forEach { (userId, server) ->
                if(userId!=this.userId){
                    server.webSocketSession.close()
                }
            }
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    fun onClose() {
        if (userId != "") {
            webSocketSet.remove(userId) //从set中删除
            val cnt = OnlineCount.decrementAndGet()
            logger.info("有连接关闭，当前连接数为：{}", cnt)
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    fun onMessage(message: String, session: Session?) {
        logger.info("来自客户端的消息：{}", message)
        val hashMap = objectMapper.readValue(message, Map::class.java)
        when {
            hashMap["type"] == REGISTER -> {
                sendMessage(this.webSocketSession, mapOf(
                        "type" to REGISTER,
                        "code" to 1
                ))
            }
            hashMap["type"] == OFFER -> {
                val sdp = hashMap["sdp"]
                //转发给其他session
                webSocketSet.forEach { (userId: String, server: WebSocketServer) ->
                    if (userId != this.userId)
                        sendMessage(server.webSocketSession, mapOf(
                                "type" to OFFER,
                                "sdp" to sdp,
                                "code" to 1
                        ))
                }
            }
            hashMap["type"] == CALL -> {
                //给其他人发IN_CALL
                webSocketSet.forEach { (userId: String, server: WebSocketServer) ->
                    println("current:${this.userId}")
                    println("inSet:$userId")
                    if (userId != this.userId)
                        sendMessage(server.webSocketSession, mapOf(
                                "type" to IN_CALL,
                                "code" to 1
                        ))
                }
            }
            hashMap["type"] == CANDIDATE -> {
                val candidate = hashMap["candidate"]
                //给其他人发IN_CALL
                webSocketSet.forEach { (userId: String, server: WebSocketServer) ->
                    if (userId != this.userId)
                        sendMessage(server.webSocketSession, mapOf(
                                "type" to CANDIDATE,
                                "candidate" to candidate,
                                "code" to 1
                        ))
                }
            }
            hashMap["type"] == MUTE -> {
                //全部禁言/解除禁言
                mute=!mute
                webSocketSet.forEach { (_: String, server: WebSocketServer) ->
                        sendMessage(server.webSocketSession, mapOf(
                                "type" to MUTE,
                                "code" to 1,
                                "value" to mute
                        ))
                }
            }
            hashMap["type"] == GET -> {
                //获取服务器数据
                webSocketSet.forEach { (_: String, server: WebSocketServer) ->
                    sendMessage(server.webSocketSession, mapOf(
                            "type" to GET,
                            "code" to 1,
                            "value" to mapOf(
                                    "count" to OnlineCount,
                                    "mute" to mute
                            )
                    ))
                }
            }
            hashMap["type"] == AUDIO_FILE -> {
                val value = hashMap["value"]
                //给其他人发语音文件
                webSocketSet.forEach { (userId: String, server: WebSocketServer) ->
                    if (userId != this.userId)
                        sendMessage(server.webSocketSession, mapOf(
                                "type" to AUDIO_FILE,
                                "code" to 1,
                                "value" to value,
                                "from" to this.userId
                        ))
                }
            }

        }
    }

    /**
     *
     * @param session
     * @param error
     */
    @OnError
    fun onError(session: Session, error: Throwable) {
        logger.error("发生错误：{}，Session ID： {}", error.message, session.id)
        error.printStackTrace()
    }

    /**
     * 发送消息，实践表明，每次浏览器刷新，session会发生变化。
     * @param message
     */
    fun sendMessage(session: Session?, message: String?) {
        try {
            session?.basicRemote?.sendText(String.format("%s (From Server，Session ID=%s)", message, session.id))
            //session.getBasicRemote().sendText(String.format("%s",message));
        } catch (e: IOException) {
            logger.error("发送消息出错：{}", e.message)
            e.printStackTrace()
        }
    }

    val objectMapper = ObjectMapper()

    /**
     * 发送消息，实践表明，每次浏览器刷新，session会发生变化。
     * @param message
     */
    fun sendMessage(session: Session?, message: Map<*, *>) {
        logger.info("发送消息：{}", message.toString())
        try {
            session?.basicRemote?.sendText(
                    objectMapper.writeValueAsString(message)
            )
        } catch (e: IOException) {
            logger.error("发送消息出错：{}", e.message)
            e.printStackTrace()
        }
    }

    /**
     * 群发消息
     * @param message
     * @throws IOException
     */
    fun broadCastInfo(message: String?) {
        for (key in webSocketSet.keys) {
            val session = webSocketSet[key]?.webSocketSession
            if (session != null && session.isOpen && userId != key) {
                sendMessage(session, message)
            }
        }
    }

    /**
     * 指定Session发送消息
     * @param message
     * @throws IOException
     */
    fun sendToUser(userId: String?, message: String?) {
        val webSocketServer = webSocketSet[userId]
        if (webSocketServer != null && webSocketServer.webSocketSession.isOpen) {
            sendMessage(webSocketServer.webSocketSession, message)
        } else {
            logger.warn("当前用户不在线：{}", userId)
        }
    }

    companion object {
        var logger: Logger = LoggerFactory.getLogger(WebSocketServer::class.java)

        //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
        private val OnlineCount = AtomicInteger(0)

        //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
        private val webSocketSet = ConcurrentHashMap<String, WebSocketServer>()

        const val REGISTER = "register"
        const val OFFER = "offer"
        const val CALL = "call"
        const val MUTE = "mute"
        const val AUDIO_FILE = "audio_file"
        const val IN_CALL = "in_call"
        const val CANDIDATE = "candidate"
        const val GET = "get"

        var mute:Boolean = false
    }
}
