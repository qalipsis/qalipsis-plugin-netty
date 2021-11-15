package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.example.socksproxy.SocksServerUtils
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest
import io.netty.handler.codec.socksx.v4.Socks4CommandType
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus
import io.qalipsis.api.logging.LoggerHelper.logger
import java.util.concurrent.atomic.AtomicInteger

@ChannelHandler.Sharable
class SocksServerHandler(
    private val requestCounter: AtomicInteger
) : SimpleChannelInboundHandler<SocksMessage>() {

    public override fun channelRead0(ctx: ChannelHandlerContext, socksRequest: SocksMessage) {
        when (socksRequest.version()) {
            SocksVersion.SOCKS4a -> {
                val socksV4CmdRequest = socksRequest as Socks4CommandRequest
                if (socksV4CmdRequest.type() === Socks4CommandType.CONNECT) {
                    ctx.pipeline().addLast(SocksServerConnectHandler())
                    ctx.pipeline().remove(this)
                    ctx.fireChannelRead(socksRequest)
                } else {
                    ctx.close()
                }
            }
            SocksVersion.SOCKS5 -> if (socksRequest is Socks5InitialRequest) {
                //ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
                ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
                ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
            } else if (socksRequest is Socks5PasswordAuthRequest) {
                ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
                ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
            } else if (socksRequest is Socks5CommandRequest) {
                if (socksRequest.type() === Socks5CommandType.CONNECT) {
                    ctx.pipeline().addLast(SocksServerConnectHandler())
                    ctx.pipeline().remove(this)
                    ctx.fireChannelRead(socksRequest)
                } else {
                    ctx.close()
                }
            } else {
                ctx.close()
            }
            else -> ctx.close()
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        requestCounter.incrementAndGet()
        ctx.flush()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable) {
        log.error(throwable) { throwable.message }
        SocksServerUtils.closeOnFlush(ctx.channel())
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
