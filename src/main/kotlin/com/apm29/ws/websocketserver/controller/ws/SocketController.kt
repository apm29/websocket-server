package com.apm29.ws.websocketserver.controller.ws

import com.apm29.ws.websocketserver.core.BaseResp
import com.apm29.ws.websocketserver.db.dao.UserRepository
import com.apm29.ws.websocketserver.db.entity.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
class Controller @Autowired constructor(
        val userRepository: UserRepository
) {

    @RequestMapping("/ws/logout", method = [RequestMethod.POST])
    fun logout(
            @RequestParam(required = true) userId: String
    ):BaseResp<User>{
        return BaseResp()
    }


    @RequestMapping("/ws/login", method = [RequestMethod.POST])
    fun loginWs(
            @RequestParam(required = true) userId: String
    ): BaseResp<User> {

        if (!userRepository.existsById(userId)) {
            userRepository.save(User(userId))
        }
        val userOption = userRepository.findById(userId)
        return BaseResp(data = userOption.get())
    }
}