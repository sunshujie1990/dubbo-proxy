package org.apache.dubbo.proxy.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.proxy.config.ConfigCenter;
import org.apache.dubbo.proxy.entity.MethodConfig;
import org.apache.dubbo.proxy.entity.ServiceConfig;
import org.apache.dubbo.proxy.entity.ServiceDefinition;
import org.apache.dubbo.proxy.service.AsyncGenericInvoker;
import org.apache.dubbo.proxy.utils.HttpTools;
import org.apache.dubbo.proxy.utils.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.HttpResponseStatus.*;


@ChannelHandler.Sharable
@Slf4j
public class HttpProcessHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private ConfigCenter configCenter;
    private AsyncGenericInvoker asyncGenericInvoker;

    public HttpProcessHandler(ConfigCenter configCenter,
                              AsyncGenericInvoker asyncGenericInvoker) {
        super();
        this.configCenter = configCenter;
        this.asyncGenericInvoker = asyncGenericInvoker;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        ServiceDefinition serviceDefinition = null;
        try {
            serviceDefinition = parseParam(msg);

            ServiceConfig serviceConfig = serviceDefinition.getServiceConfig();

            Map<String, ServiceConfig> serviceMapping =
                    configCenter.getServiceMapping();
            ServiceConfig mappedServiceCd = serviceMapping.get(serviceConfig.getInterfaceName());

            if (mappedServiceCd == null && configCenter.getProxyConfig().getStrict()) {
                HttpTools.writeError(ctx, "service [" + serviceConfig.getInterfaceName() + "] not found!", NOT_FOUND);
                return;
            }

            if (mappedServiceCd != null) {
                serviceDefinition.setServiceConfig(mappedServiceCd);
            }

            doRequest(ctx, serviceDefinition, HttpUtil.isKeepAlive(msg));
        } catch (IllegalArgumentException e) {
            log.error(msg.toString(), e);
            HttpTools.writeError(ctx, e.getMessage(), BAD_REQUEST);
        } catch (Exception e) {
            log.error(msg.toString(), e);
            HttpTools.writeError(ctx, e.getMessage(), INTERNAL_SERVER_ERROR);
        }
    }

    private static ServiceDefinition parseParam(FullHttpRequest msg) {
        try {
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(msg.uri());
            Map<String, List<String>> parameters = queryStringDecoder.parameters();
            List<String> group = parameters.get("group");
            List<String> version = parameters.get("version");
            String path = queryStringDecoder.rawPath();
            StringTokenizer stringTokenizer = new StringTokenizer(path, "/");
            ByteBuf raw = msg.content();
            byte[] bytes = ByteBufUtil.getBytes(raw);
            ServiceDefinition serviceDefinition = new ServiceDefinition();
            MethodConfig methodConfig = JsonUtils.parseObject(bytes, MethodConfig.class);
            serviceDefinition.setMethodConfig(methodConfig);
            ServiceConfig serviceConfig = new ServiceConfig();
            serviceConfig.setGroup(CollectionUtils.isEmpty(group) ? null : group.get(0));
            serviceConfig.setVersion(CollectionUtils.isEmpty(version) ? null : version.get(0));
            serviceConfig.setInterfaceName(stringTokenizer.nextToken());
            serviceDefinition.setServiceConfig(serviceConfig);
            return serviceDefinition;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

    }

    private void doRequest(ChannelHandlerContext ctx, ServiceDefinition serviceDefinition, boolean keepalive) {

        CompletableFuture<Object> resultFuture = asyncGenericInvoker.genericCall(serviceDefinition);

        resultFuture.whenComplete(new BiConsumer<Object, Throwable>() {
            @Override
            public void accept(Object r, Throwable t) {
                if (r != null) {
                    HttpTools.writeOK(ctx, r, keepalive);
                } else {
                    HttpTools.writeError(ctx, t.getMessage(), INTERNAL_SERVER_ERROR);
                    log.error("exception occured", t);
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
