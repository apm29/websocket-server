package com.apm29.ws.websocketserver.db.dao

import com.apm29.ws.websocketserver.db.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository:JpaRepository<User,String>