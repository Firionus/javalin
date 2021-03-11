/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.websocket

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import java.util.*

/**
 * Is instantiated for every WebSocket connection. It keeps the session and sessionId and handles incoming events by
 * delegating to the registered before, endpoint, after and logger handlers.
 */
@WebSocket
class WsConnection(val matcher: WsPathMatcher, val exceptionMapper: WsExceptionMapper, val wsLogger: WsHandlers?) {

    private val sessionId: String = UUID.randomUUID().toString()

    @OnWebSocketConnect
    fun onConnect(session: Session) {
        val ctx = WsConnectContext(sessionId, session)
        tryBeforeAndEndpointHandlers(ctx) { it.handlers.wsConnectHandler?.handleConnect(ctx) }
        tryAfterHandlers(ctx) { it.handlers.wsConnectHandler?.handleConnect(ctx) }
        wsLogger?.wsConnectHandler?.handleConnect(ctx)
    }

    @OnWebSocketMessage
    fun onMessage(session: Session, message: String) {
        val ctx = WsMessageContext(sessionId, session, message)
        tryBeforeAndEndpointHandlers(ctx) { it.handlers.wsMessageHandler?.handleMessage(ctx) }
        tryAfterHandlers(ctx) { it.handlers.wsMessageHandler?.handleMessage(ctx) }
        wsLogger?.wsMessageHandler?.handleMessage(ctx)
    }

    @OnWebSocketMessage
    fun onMessage(session: Session, buffer: ByteArray, offset: Int, length: Int) {
        val ctx = WsBinaryMessageContext(sessionId, session, buffer, offset, length)
        tryBeforeAndEndpointHandlers(ctx) { it.handlers.wsBinaryMessageHandler?.handleBinaryMessage(ctx) }
        tryAfterHandlers(ctx) { it.handlers.wsBinaryMessageHandler?.handleBinaryMessage(ctx) }
        wsLogger?.wsBinaryMessageHandler?.handleBinaryMessage(ctx)
    }

    @OnWebSocketClose
    fun onClose(session: Session, statusCode: Int, reason: String?) {
        val ctx = WsCloseContext(sessionId, session, statusCode, reason)
        tryBeforeAndEndpointHandlers(ctx) { it.handlers.wsCloseHandler?.handleClose(ctx) }
        tryAfterHandlers(ctx) { it.handlers.wsCloseHandler?.handleClose(ctx) }
        wsLogger?.wsCloseHandler?.handleClose(ctx)
    }

    @OnWebSocketError
    fun onError(session: Session, throwable: Throwable?) {
        val ctx = WsErrorContext(sessionId, session, throwable)
        tryBeforeAndEndpointHandlers(ctx) { it.handlers.wsErrorHandler?.handleError(ctx) }
        tryAfterHandlers(ctx) { it.handlers.wsErrorHandler?.handleError(ctx) }
        wsLogger?.wsErrorHandler?.handleError(ctx)
    }

    private fun tryBeforeAndEndpointHandlers(ctx: WsContext, handle: (WsEntry) -> Unit) {
        val requestUri = ctx.session.uriNoContextPath()
        try {
            matcher.findBeforeHandlerEntries(requestUri).forEach { handle.invoke(it) }
            matcher.findEndpointHandlerEntry(requestUri)!!.let { handle.invoke(it) } // never null, 404 is handled in front
        } catch (e: Exception) {
            exceptionMapper.handle(e, ctx)
        }
    }

    private fun tryAfterHandlers(ctx: WsContext, handle: (WsEntry) -> Unit) {
        val requestUri = ctx.session.uriNoContextPath()
        try {
            matcher.findAfterHandlerEntries(requestUri).forEach { handle.invoke(it) }
        } catch (e: Exception) {
            exceptionMapper.handle(e, ctx)
        }
    }

}

private fun Session.uriNoContextPath() = this.upgradeRequest.requestURI.path.removePrefix((this.upgradeRequest as ServletUpgradeRequest).httpServletRequest.contextPath)
