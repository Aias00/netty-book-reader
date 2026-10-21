package book;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public final class LineClient {
    public static void main(String[] args) throws InterruptedException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel ch = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel c) {
                        c.pipeline().addLast(new LineBasedFrameDecoder(2048),
                            new StringDecoder(CharsetUtil.UTF_8),
                            new StringEncoder(CharsetUtil.UTF_8),
                            new SimpleChannelInboundHandler<String>() {
                                @Override protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                    System.out.println(msg);
                                }
                                @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace(); ctx.close();
                                }
                            });
                    }
                }).connect("127.0.0.1", port).sync().channel();
            ch.writeAndFlush("你好，Netty\nquit\n").sync();
            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }
}
