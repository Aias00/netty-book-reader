package book;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public final class LineServer {
    public static void main(String[] args) throws InterruptedException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            Channel server = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new LineBasedFrameDecoder(1024),
                            new StringDecoder(CharsetUtil.UTF_8),
                            new StringEncoder(CharsetUtil.UTF_8),
                            new ReplyHandler());
                    }
                }).bind("127.0.0.1", port).sync().channel();
            System.out.println("LISTENING " + port);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    static final class ReplyHandler extends SimpleChannelInboundHandler<String> {
        @Override protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            if ("quit".equals(msg)) {
                ctx.writeAndFlush("再见\n").addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.writeAndFlush("收到：" + msg + "\n");
            }
        }
        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
