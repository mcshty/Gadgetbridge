package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class XiaomiSppPacket {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiSppPacket.class);

    public static final byte[] PACKET_PREAMBLE = new byte[]{(byte) 0xba, (byte) 0xdc, (byte) 0xfe};
    public static final byte[] PACKET_EPILOGUE = new byte[]{(byte) 0xef};

    public static final int CHANNEL_VERSION = 0;
    /**
     * Channel ID for PROTO messages received from device
     */
    public static final int CHANNEL_PROTO_RX = 1;

    /**
     * Channel ID for PROTO messages sent to device
     */
    public static final int CHANNEL_PROTO_TX = 2;
    public static final int CHANNEL_FITNESS = 3;
    public static final int CHANNEL_VOICE = 4;
    public static final int CHANNEL_MASS = 5;
    public static final int CHANNEL_OTA = 7;

    public static final int DATA_TYPE_PLAIN = 0;
    public static final int DATA_TYPE_ENCRYPTED = 1;
    public static final int DATA_TYPE_AUTH = 2;

    private XiaomiAuthService authService;
    private byte[] payload;
    private boolean flag, needsResponse;
    private int channel, opCode, frameSerial, dataType;

    public static class Builder {
        private XiaomiAuthService authService;
        private byte[] payload = null;
        private boolean flag = false, needsResponse = false;
        private int channel = -1, opCode = -1, frameSerial = -1, dataType = -1;

        public XiaomiSppPacket build() {
            XiaomiSppPacket result = new XiaomiSppPacket();

            result.channel = channel;
            result.flag = flag;
            result.needsResponse = needsResponse;
            result.opCode = opCode;
            result.frameSerial = frameSerial;
            result.dataType = dataType;
            result.payload = payload;
            result.authService = authService;

            return result;
        }

        public Builder channel(final int channel) {
            this.channel = channel;
            return this;
        }

        public Builder flag(final boolean flag) {
            this.flag = flag;
            return this;
        }

        public Builder needsResponse(final boolean needsResponse) {
            this.needsResponse = needsResponse;
            return this;
        }

        public Builder opCode(final int opCode) {
            this.opCode = opCode;
            return this;
        }

        public Builder frameSerial(final int frameSerial) {
            this.frameSerial = frameSerial;
            return this;
        }

        public Builder dataType(final int dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder payload(final byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Builder authService(final XiaomiAuthService authService) {
            this.authService = authService;
            return this;
        }
    }

    public int getChannel() {
        return channel;
    }

    public int getDataType() {
        return dataType;
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean needsResponse() {
        return needsResponse;
    }

    public boolean hasFlag() {
        return this.flag;
    }

    public static XiaomiSppPacket fromXiaomiCommand(final XiaomiProto.Command command, XiaomiAuthService authService, int frameCounter, boolean needsResponse) {
        return newBuilder().channel(CHANNEL_PROTO_TX).flag(true).needsResponse(needsResponse).dataType(
                command.getType() == XiaomiAuthService.COMMAND_TYPE && command.getSubtype() >= 17 ? DATA_TYPE_AUTH : DATA_TYPE_ENCRYPTED
        ).frameSerial(frameCounter).opCode(2).payload(command.toByteArray()).authService(authService).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String toString() {
        return String.format(Locale.ROOT,
                "SppPacket{ channel=0x%x, flag=%b, needsResponse=%b, opCode=0x%x, frameSerial=0x%x, dataType=0x%x, payloadSize=%d }",
                channel, flag, needsResponse, opCode, frameSerial, dataType, payload.length);
    }

    public static XiaomiSppPacket decode(final byte[] packet) {
        if (packet.length < 11) {
            LOG.error("Cannot decode incomplete packet");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        byte[] preamble = new byte[PACKET_PREAMBLE.length];
        buffer.get(preamble);

        if (!Arrays.equals(PACKET_PREAMBLE, preamble)) {
            LOG.error("Expected preamble (0x{}) does not match found preamble (0x{})",
                    GB.hexdump(PACKET_PREAMBLE),
                    GB.hexdump(preamble));
            return null;
        }

        byte channel = buffer.get();

        if ((channel & 0xf0) != 0) {
            LOG.warn("Reserved bits in channel byte are non-zero: 0b{}", Integer.toBinaryString((channel & 0xf0) >> 4));
            channel = 0x0f;
        }

        byte flags = buffer.get();
        boolean flag = (flags & 0x80) != 0;
        boolean needsResponse = (flags & 0x40) != 0;

        if ((flags & 0x0f) != 0) {
            LOG.warn("Reserved bits in flags byte are non-zero: 0b{}", Integer.toBinaryString(flags & 0x0f));
        }

        // payload header is included in size
        int payloadLength = (buffer.getShort() & 0xffff) - 3;

        if (payloadLength + 11 > packet.length) {
            LOG.error("Packet incomplete (expected length: {}, actual length: {})", payloadLength + 11, packet.length);
            return null;
        }

        int opCode = buffer.get() & 0xff;
        int frameSerial = buffer.get() & 0xff;
        int dataType = buffer.get() & 0xff;
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        byte[] epilogue = new byte[PACKET_EPILOGUE.length];
        buffer.get(epilogue);

        if (!Arrays.equals(PACKET_EPILOGUE, epilogue)) {
            LOG.error("Expected epilogue (0x{}) does not match actual epilogue (0x{})",
                    GB.hexdump(PACKET_EPILOGUE),
                    GB.hexdump(epilogue));
            return null;
        }

        XiaomiSppPacket result = new XiaomiSppPacket();
        result.channel = channel;
        result.flag = flag;
        result.needsResponse = needsResponse;
        result.opCode = opCode;
        result.frameSerial = frameSerial;
        result.dataType = dataType;
        result.payload = payload;

        return result;
    }

    public byte[] encode(final AtomicInteger encryptionCounter) {
        byte[] payload = this.payload;

        if (dataType == DATA_TYPE_ENCRYPTED) {
            short packetCounter = (short) encryptionCounter.incrementAndGet();
            payload = authService.encrypt(payload, packetCounter);
            payload = ByteBuffer.allocate(payload.length + 2).order(ByteOrder.LITTLE_ENDIAN).putShort(packetCounter).put(payload).array();
        }

        ByteBuffer buffer = ByteBuffer.allocate(11 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(PACKET_PREAMBLE);

        buffer.put((byte) (channel & 0xf));
        buffer.put((byte) ((flag ? 0x80 : 0) | (needsResponse ? 0x40 : 0)));
        buffer.putShort((short) (payload.length + 3));

        buffer.put((byte) (opCode & 0xff));
        buffer.put((byte) (frameSerial & 0xff));
        buffer.put((byte) (dataType & 0xff));

        buffer.put(payload);

        buffer.put(PACKET_EPILOGUE);
        return buffer.array();
    }
}
