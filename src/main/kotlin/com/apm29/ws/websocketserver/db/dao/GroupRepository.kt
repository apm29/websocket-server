package com.apm29.ws.websocketserver.db.dao

import com.apm29.ws.websocketserver.db.entity.Group
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository:JpaRepository<Group,String>