package com.apm29.ws.websocketserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebsocketServerApplication

fun main(args: Array<String>) {
    runApplication<WebsocketServerApplication>(*args)
}
