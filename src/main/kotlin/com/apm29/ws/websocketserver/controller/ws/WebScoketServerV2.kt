package com.apm29.ws.websocketserver.controller.ws

import com.apm29.ws.websocketserver.config.WSEndpointConfig
import com.apm29.ws.websocketserver.db.dao.GroupRepository
import com.apm29.ws.websocketserver.db.dao.GroupUserRepository
import com.apm29.ws.websocketserver.db.dao.UserRepository
import com.apm29.ws.websocketserver.db.entity.Group
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.websocket.*
import javax.websocket.server.PathParam
import javax.websocket.server.ServerEndpoint


/**
 * @ServerEndpoint 注解是一个类层次的注解，它的功能主要是将目前的类定义成一个websocket服务器端,
 * 注解的值将被用于监听用户连接的终端访问URL地址,客户端可以通过这个URL来连接到WebSocket服务器端
 */

@ServerEndpoint("/websocket/v2/{userId}", configurator = WSEndpointConfig::class)
@Component
class WebSocketServerV2 @Autowired constructor(
        val userRepository: UserRepository,
        val groupRepository: GroupRepository,
        val groupUserRepository: GroupUserRepository
) {
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private lateinit var webSocketSession: Session

    //当前发消息的人员userId
    private lateinit var userId: String

    //gson
    private val gson: Gson = GsonBuilder()
            .create()

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
        val signalMessage = try {
            gson.fromJson<SignalMessage>(message, SignalMessage::class.java)
        } catch (e: Exception) {
            Fail()
        }
        when (signalMessage.type) {
            WebSocketTypes.Register.type -> {
                //返回im相关信息
                val groups = groupUserRepository.findAllGroupByUserId(signalMessage.from ?: userId)
                webSocketSession.basicRemote.sendText(
                        gson.toJson(SignalMessage(type = WebSocketTypes.Register.type, info = ImPttInfo(
                                signalMessage.from,
                                groups
                        )))
                )
            }
            else -> {

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


    companion object {
        var logger: Logger = LoggerFactory.getLogger(WebSocketServerV2::class.java)

        //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
        private val OnlineCount = AtomicInteger(0)

        //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
        private val webSocketSet = ConcurrentHashMap<String, WebSocketServerV2>()

    }
}

//WebSocket 消息类型
sealed class WebSocketTypes(val type: String) {
    object Fail : WebSocketTypes("fail")
    object Register : WebSocketTypes("register")
    object JoinGroup : WebSocketTypes("create_join_group")
    object Offer : WebSocketTypes("offer")
    object Answer : WebSocketTypes("answer")
    object Call : WebSocketTypes("call")
    object Joined : WebSocketTypes("joined")
    object InCall : WebSocketTypes("in_call")
    object Candidate : WebSocketTypes("candidate")
}

open class SignalMessage(
        val id: String = UUID.randomUUID().toString(),
        val type: String,
        val from: String? = null,
        val to: String? = null,
        val groupId: String? = null,
        val candidate: IceCandidate? = null,
        val sdp: SessionDescription? = null,
        val info: ImPttInfo? = null,
        val groupUsers:List<String> = arrayListOf()
)

data class ImPttInfo(
        //用户id
        val id: String? = null,
        //用户群组
        val groups: List<Group>? = arrayListOf()
)

/**
 * 失败消息
 */
class Fail : SignalMessage(
        type = WebSocketTypes.Fail.type
)

class IceCandidate(
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = 0,
        val sdp: String? = null,
        val serverUrl: String? = null,
        val adapterType: AdapterType? = null
)

enum class AdapterType(bitMask: Int) {
    UNKNOWN(0),
    ETHERNET(1),
    WIFI(2),
    CELLULAR(4),
    VPN(8),
    LOOPBACK(16),
    ADAPTER_TYPE_ANY(32),
    CELLULAR_2G(64),
    CELLULAR_3G(128),
    CELLULAR_4G(256),
    CELLULAR_5G(512);
}

class SessionDescription(
        val type: Type? = null,
        val description: String? = null
)

enum class Type {
    OFFER,
    PRANSWER,
    ANSWER;
}