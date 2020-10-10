package com.apm29.ws.websocketserver.db.dao

import com.apm29.ws.websocketserver.db.entity.Group
import com.apm29.ws.websocketserver.db.entity.GroupUser
import com.apm29.ws.websocketserver.db.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GroupUserRepository : JpaRepository<GroupUser, String> {
    @Query("SELECT ig.groupId FROM im_group ig LEFT JOIN im_group_user igu ON igu.groupId = ig.groupId WHERE igu.userId = :userId ")
    fun findAllGroupByUserId(userId: String): List<Group>

    @Query("SELECT iu.userId FROM im_group_user igu LEFT JOIN im_user iu ON igu.userId = iu.userId  where igu.groupId = :groupId")
    fun findAllUserByGroupId(groupId: String?):List<User>
}