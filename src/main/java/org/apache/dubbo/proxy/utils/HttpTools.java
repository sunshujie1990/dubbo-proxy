package org.apache.dubbo.proxy.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/14
 */
public class HttpTools {
    public static void writeOK(ChannelHandlerContext ctx, Object result, boolean keepAlive) {
        writeResponse(ctx, result, keepAlive, OK);
    }

    public static void writeError(ChannelHandlerContext ctx, String message, HttpResponseStatus status) {
        writeResponse(ctx, message, false, status);
    }

    public static void writeResponse(ChannelHandlerContext ctx, Object result, boolean keepAlive, HttpResponseStatus status) {
        ByteBuf bf = result == null ? Unpooled.EMPTY_BUFFER :
                Unpooled.copiedBuffer(JsonUtils.writeValueAsString(result), CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, bf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        if (keepAlive) {
            HttpUtil.setKeepAlive(response, true);
        }
        // Write the response.
        ChannelFuture writeFuture = ctx.writeAndFlush(response);
        if (!keepAlive) {
            writeFuture.addListener(CLOSE);
        }
    }
}
