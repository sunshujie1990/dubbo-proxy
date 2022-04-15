package org.apache.dubbo.proxy.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.dubbo.proxy.config.ConfigCenter;
import org.apache.dubbo.proxy.service.AsyncGenericInvoker;
import org.apache.dubbo.proxy.utils.NamingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class NettyServer {

    private Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final ExecutorService serverStartor = Executors
            .newSingleThreadExecutor(new NamingThreadFactory(
                    "Dubbo-proxy-starter"));

    private final ConfigCenter configCenter;
    private final AsyncGenericInvoker asyncGenericInvoker;

    @Autowired
    public NettyServer(ConfigCenter configCenter, AsyncGenericInvoker asyncGenericInvoker) {
        this.configCenter = configCenter;
        this.asyncGenericInvoker = asyncGenericInvoker;
    }


    @PostConstruct
    public void start() {
        serverStartor.execute(() -> {
            init();
            try {
                ChannelFuture f = bootstrap.bind(configCenter.getProxyConfig().getBind(),
                        configCenter.getProxyConfig().getPort()).sync();
                logger.info("Dubbo proxy started, {}", configCenter.getProxyConfig());
                f.channel().closeFuture().sync();
                logger.info("Dubbo proxy closed, {}", configCenter.getProxyConfig());
            } catch (InterruptedException e) {
                logger.error("dubbo proxy start failed", e);
            } finally {
                destroy();
            }
        });

    }

    private void init() {
        bootstrap = new ServerBootstrap();
        // reactor模型一个acceptor线程就够了
        bossGroup = new NioEventLoopGroup(1, new NamingThreadFactory("Dubbo-Proxy-Boss"));
        // 纯异步调用 worker线程池无需太大
        workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2,
                new NamingThreadFactory("Dubbo-Proxy-Worker"));
        HttpProcessHandler processHandler = new HttpProcessHandler(configCenter, asyncGenericInvoker);
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childHandler(new ProxyChannelInitializer(processHandler))
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    @PreDestroy
    public void destroy() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        serverStartor.shutdown();
    }

    private class ProxyChannelInitializer extends
            ChannelInitializer<SocketChannel> {

        private HttpProcessHandler httpProcessHandler;

        public ProxyChannelInitializer(HttpProcessHandler httpProcessHandler) {
            this.httpProcessHandler = httpProcessHandler;
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(
                    new LoggingHandler(NettyServer.class, LogLevel.DEBUG),
                    new HttpServerCodec(), new HttpObjectAggregator(512 * 1024 * 1024),
                    httpProcessHandler);
        }
    }
}
