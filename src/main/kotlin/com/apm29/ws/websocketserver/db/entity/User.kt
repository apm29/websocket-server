package com.apm29.ws.websocketserver.db.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity(name = "im_user")
data class User @JvmOverloads constructor(
        @field:Id
        @field:Column(name = "user_id")
        val userId: String? = null
)

@Entity(name = "im_group")
data class Group @JvmOverloads constructor(
        @field:Id
        @field:Column(name = "group_id")
        val groupId: String? = null
)

@Entity(name = "im_group_user")
data class GroupUser @JvmOverloads constructor(
        @field:Id
        @field:Column
        val id: String?= null,

        @field:Column(name = "group_id")
        val groupId: String? = null,

        @field:Column(name = "user_id")
        val userId: String?= null
)