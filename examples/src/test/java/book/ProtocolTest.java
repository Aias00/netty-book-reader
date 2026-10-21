package book;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.*;
import io.netty.handler.codec.string.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private EmbeddedChannel channel() {
        return new EmbeddedChannel(new LineBasedFrameDecoder(1024),
            new StringDecoder(CharsetUtil.UTF_8), new StringEncoder(CharsetUtil.UTF_8),
            new LineServer.ReplyHandler());
    }
    private String take(EmbeddedChannel ch) {
        ByteBuf b = ch.readOutbound();
        assertNotNull(b);
        try { return b.toString(CharsetUtil.UTF_8); } finally { b.release(); }
    }
    @Test void splitAndCombinedMessages() {
        EmbeddedChannel ch = channel();
        try {
            ch.writeInbound(Unpooled.copiedBuffer("he", CharsetUtil.UTF_8));
            assertNull(ch.readOutbound());
            ch.writeInbound(Unpooled.copiedBuffer("llo\nworld\n", CharsetUtil.UTF_8));
            assertEquals("收到：hello\n", take(ch));
            assertEquals("收到：world\n", take(ch));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void utf8MaySplitInsideCharacter() {
        EmbeddedChannel ch = channel();
        byte[] bytes = "你\n".getBytes(CharsetUtil.UTF_8);
        try {
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{bytes[0]}));
            assertNull(ch.readOutbound());
            ch.writeInbound(Unpooled.wrappedBuffer(java.util.Arrays.copyOfRange(bytes,1,bytes.length)));
            assertEquals("收到：你\n",take(ch));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void quitFlushesThenCloses() {
        EmbeddedChannel ch = channel();
        try {
            ch.writeInbound(Unpooled.copiedBuffer("quit\n", CharsetUtil.UTF_8));
            assertEquals("再见\n",take(ch));
            assertFalse(ch.isOpen());
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void oversizedLineRejected() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8, true, true));
        try {
            assertThrows(TooLongFrameException.class, () -> ch.writeInbound(
                Unpooled.copiedBuffer("123456789",CharsetUtil.UTF_8)));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void lengthFieldSurvivesFragmentation() {
        EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(1024,0,4,0,4));
        try {
            ByteBuf first = Unpooled.buffer().writeInt(3).writeByte(1);
            assertFalse(ch.writeInbound(first));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{2,3})));
            ByteBuf result = ch.readInbound();
            try { assertEquals(3,result.readableBytes());assertEquals(1,result.readByte());assertEquals(2,result.readByte());assertEquals(3,result.readByte()); }
            finally { result.release(); }
        } finally { ch.finishAndReleaseAll(); }
    }
}
