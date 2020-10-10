package com.apm29.ws.websocketserver.controller.ws

import com.apm29.ws.websocketserver.config.WSEndpointConfig
import com.apm29.ws.websocketserver.db.dao.GroupRepository
import com.apm29.ws.websocketserver.db.dao.GroupUserRepository
import com.apm29.ws.websocketserver.db.dao.UserRepository
import com.apm29.ws.websocketserver.db.entity.Group
import com.apm29.ws.websocketserver.db.entity.GroupUser
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

@ServerEndpoint("/websocket/v2/{userId}")
@Component
class WebSocketServerV2  {
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private lateinit var webSocketSession: Session

    //当前发消息的人员userId
    private lateinit var userId: String

    //gson
    private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

    /**
     * 连接建立成功调用的方法 */
    @OnOpen
    fun onOpen(@PathParam(value = "userId") param: String, session: Session, config: EndpointConfig?) {
        this.userId = param
        logger.info("userId:{}",userId);
        this.webSocketSession = session
        webSocketSet[param] = this //加入map中
        webSocketSet.forEach{
            logger.warn("${it.key}:${it.value.userId}:${it.value.webSocketSession}")
        }
        val cnt = OnlineCount.incrementAndGet() // 在线数加1
        logger.info("有连接加入，当前连接数为：{} {}", cnt,webSocketSet.keys.reduce { acc, s -> "$acc,$s" })
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    fun onClose() {
        if (userId != "") {
            webSocketSet.remove(userId) //从set中删除
            val cnt = OnlineCount.decrementAndGet()
            logger.info("有连接关闭，当前连接数为：{} {}", cnt, webSocketSet.keys.reduce { acc, s -> "$acc,$s" })
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    fun onMessage(message: String, session: Session?) {

        val signalMessage = try {
            gson.fromJson(message, SignalMessage::class.java)
        } catch (e: Exception) {
            Fail()
        }
        logger.info("来自客户端的消息：{}", signalMessage)
        val groupId = signalMessage.groupId
        val id = signalMessage.id
        val from = signalMessage.from
        val to = signalMessage.to
        val sdp = signalMessage.sdp
        val candidate = signalMessage.candidate
        when (val type = signalMessage.type) {
            //注册到信令服务器
            WebSocketTypes.Register.type -> {
                //返回im相关信息
                try {
                    val groups = groupUserRepository.findAllGroupByUserId(from?:userId)
                    sendMessage(
                            SignalMessage(
                                    id = signalMessage.id,
                                    type = type,
                                    info = ImPttInfo(from, groups)
                            )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendErrorMessage(id, "信令服务器注册失败")
                }
            }
            //加入群组
            WebSocketTypes.JoinGroup.type -> {
                if (groupId != null) {
                    try {
                        val group = groupRepository.findById(
                                groupId
                        )
                        if (!group.isPresent) {
                            groupRepository.save(Group(groupId))
                        }
                        groupUserRepository.save(
                                GroupUser(
                                        groupId = groupId,
                                        userId = from
                                )
                        )
                        sendMessage(
                                SignalMessage(
                                        id = id,
                                        type = type
                                )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        sendErrorMessage(id, "加入群组失败")
                    }
                } else {
                    sendErrorMessage(id, "groupId不能为空")
                }
            }
            WebSocketTypes.Call.type -> {
                try {//接到呼叫,返回群组人员列表,给其他人员发送InCall
                    val groupUsers = groupUserRepository.findAllUserByGroupId(groupId)
                    val others = groupUsers.filter { it.userId != from }
                    //回复caller
                    sendMessage(
                            SignalMessage(
                                    id = id,
                                    type = type,
                                    groupId = groupId,
                                    groupUsers = others.map {
                                        it.userId ?: "UNKNOWN-USER-ID"
                                    },
                                    from = from
                            )
                    )
                    //给其他人员发送InCall
                    others.forEach { user ->
                        sendMessage(
                                SignalMessage(
                                        type = WebSocketTypes.InCall.type,
                                        from = from,
                                        groupId = groupId,
                                        to = user.userId
                                ),
                                webSocketSet[user.userId]?.webSocketSession
                        )
                    }
                } catch (e: Exception) {
                    sendErrorMessage(id, "呼叫失败:请联系管理员")
                    e.printStackTrace()
                }
            }
            WebSocketTypes.Offer.type ->{
                //将Offer转发给其他Callee
                val groupUsers = groupUserRepository.findAllUserByGroupId(groupId)
                val others =  groupUsers.filter { it.userId != from }

                others.forEach { user ->
                    val serverV2 = webSocketSet[user.userId]
                    sendMessage(
                            SignalMessage(
                                    type = WebSocketTypes.Offer.type,
                                    from = from,
                                    groupId = groupId,
                                    to = user.userId,
                                    sdp = sdp
                            ),
                            serverV2?.webSocketSession
                    )
                }
                sendSuccessMessage(id)
            }
            WebSocketTypes.Answer.type ->{
                //转发消息给其他Callee
                val groupUsers = groupUserRepository.findAllUserByGroupId(groupId)
                val others =  groupUsers.filter { it.userId != from }
                others.forEach { user ->
                    sendMessage(
                            SignalMessage(
                                    type = WebSocketTypes.Answer.type,
                                    from = from,
                                    groupId = groupId,
                                    to = user.userId,
                                    sdp = sdp
                            ),
                            webSocketSet[user.userId]?.webSocketSession
                    )
                }
                sendSuccessMessage(id)
            }
            WebSocketTypes.Candidate.type ->{
                //转发candidate给callee
                val groupUsers = groupUserRepository.findAllUserByGroupId(groupId)
                val others =  groupUsers.filter { it.userId != from }
                println(others)
                println(webSocketSet.size)
                others.forEach { user ->
                    sendMessage(
                            SignalMessage(
                                    type = WebSocketTypes.Candidate.type,
                                    from = from,
                                    groupId = groupId,
                                    to = user.userId,
                                    candidate = candidate,
                                    sdp = sdp
                            ),
                            webSocketSet[user.userId]?.webSocketSession
                    )
                }
                sendSuccessMessage(id)
            }
            else -> {

            }
        }

    }

    private fun sendMessage(message: SignalMessage, target: Session? = webSocketSession) {
        logger.info("服务端发送:{}",message)
        if(target!=null){
            target.basicRemote?.sendText(
                    gson.toJson(message)
            )
        }else{
            WebSocketServer.logger.info("无效的WS链接--->$target")
            WebSocketServer.logger.info("${message.from}--->${webSocketSet[message.from]}")
            WebSocketServer.logger.info("${message.to}--->${webSocketSet[message.to]}")
        }
    }

    private fun sendErrorMessage(id: String, reason: String) {
        webSocketSession.basicRemote.sendText(
                gson.toJson(
                        SignalMessage(
                                id = id,
                                type = WebSocketTypes.Fail.type,
                                error = reason
                        )
                )
        )
    }

    private fun sendSuccessMessage(id: String) {
        webSocketSession.basicRemote.sendText(
                gson.toJson(
                        SignalMessage(
                                id = id,
                                type = WebSocketTypes.Success.type
                        )
                )
        )
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

    @Autowired
    fun setRepos(
            userRepository: UserRepository,
            groupRepository: GroupRepository,
            groupUserRepository: GroupUserRepository
    ){
        WebSocketServerV2.userRepository = userRepository
        WebSocketServerV2.groupRepository = groupRepository
        WebSocketServerV2.groupUserRepository = groupUserRepository
    }

    companion object {

        @JvmStatic lateinit var userRepository: UserRepository
        @JvmStatic lateinit var groupRepository: GroupRepository
        @JvmStatic lateinit var groupUserRepository: GroupUserRepository

        var logger: Logger = LoggerFactory.getLogger(WebSocketServerV2::class.java)

        //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
        private val OnlineCount = AtomicInteger(0)

        // concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
        // 若要实现服务端与单一客户端通信的话，可以使用Map来存放，其中Key可以为用户标识
        private val webSocketSet = ConcurrentHashMap<String, WebSocketServerV2>()

    }
}

//WebSocket 消息类型
sealed class WebSocketTypes(val type: String) {
    object Fail : WebSocketTypes("fail")
    object Success : WebSocketTypes("success")
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
        val groupUsers: List<String> = arrayListOf(),
        val error: String? = null
){
    override fun toString(): String {
        return "SignalMessage(id='$id', type='$type', from=$from, to=$to, groupId=$groupId,  info=$info, groupUsers=$groupUsers, error=$error)"
    }
}

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