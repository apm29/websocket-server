package com.apm29.ws.websocketserver.controller.ws

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.websocket.*
import javax.websocket.server.PathParam
import javax.websocket.server.ServerEndpoint

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 */
@ServerEndpoint("/websocket/{userId}")
@Component
class WebSocketServer {
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private var WebSocketsession: Session? = null

    //当前发消息的人员userId
    private var userId = ""

    /**
     * 连接建立成功调用的方法 */
    @OnOpen
    fun onOpen(@PathParam(value = "userId") param: String, WebSocketsession: Session?, config: EndpointConfig?) {
        userId = param
        //log.info("authKey:{}",authKey);
        this.WebSocketsession = WebSocketsession
        webSocketSet[param] = this //加入map中
        val cnt = OnlineCount.incrementAndGet() // 在线数加1
        logger.info("有连接加入，当前连接数为：{}", cnt)
        sendMessage(this.WebSocketsession, "连接成功")
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
        sendMessage(session, "收到消息，消息内容：$message")
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
            session!!.basicRemote.sendText(String.format("%s (From Server，Session ID=%s)", message, session.id))
            //session.getBasicRemote().sendText(String.format("%s",message));
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
            val session = webSocketSet[key]!!.WebSocketsession
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
        if (webSocketServer != null && webSocketServer.WebSocketsession!!.isOpen) {
            sendMessage(webSocketServer.WebSocketsession, message)
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
    }
}