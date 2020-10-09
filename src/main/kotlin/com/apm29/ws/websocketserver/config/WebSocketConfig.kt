package com.apm29.ws.websocketserver.config

import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.server.standard.ServerEndpointExporter
import javax.websocket.server.ServerEndpointConfig


@Configuration
class WebSocketConfig {
    @Bean
    fun serverEndpointExporter(): ServerEndpointExporter {
        return ServerEndpointExporter()
    }
}

/**
 * 用于为WebsocketEndPoint注入bean
 * https://syso.site/article/73
 */
@Component
class WSEndpointConfig : ServerEndpointConfig.Configurator(), ApplicationContextAware {
    override fun <T> getEndpointInstance(clazz: Class<T>): T {
        return context!!.getBean(clazz)
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }

    companion object {
        @Volatile
        private var context: BeanFactory? = null
    }
}