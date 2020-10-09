package com.apm29.ws.websocketserver.db.dao

import com.apm29.ws.websocketserver.db.entity.Group
import com.apm29.ws.websocketserver.db.entity.GroupUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GroupUserRepository : JpaRepository<GroupUser, String> {
    @Query("SELECT ig.groupId FROM im_group ig LEFT JOIN im_group_user igu ON igu.groupId = ig.groupId WHERE igu.userId = :userId ")
    fun findAllGroupByUserId(userId: String): List<Group>
}