package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacket.CHANNEL_FITNESS;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacket.CHANNEL_PROTO_RX;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacket.CHANNEL_PROTO_TX;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacket.PACKET_PREAMBLE;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.AbstractBTBRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;

public class XiaomiSppSupport extends XiaomiSupport {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiSppSupport.class);

    AbstractBTBRDeviceSupport commsSupport = new AbstractBTBRDeviceSupport(LOG) {
        @Override
        public boolean useAutoConnect() {
            return XiaomiSppSupport.this.useAutoConnect();
        }

        @Override
        public void onSocketRead(byte[] data) {
            XiaomiSppSupport.this.onSocketRead(data);
        }

        @Override
        public boolean getAutoReconnect() {
            return XiaomiSppSupport.this.getAutoReconnect();
        }

        @Override
        protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
            // FIXME unsetDynamicState unsets the fw version, which causes problems..
            if (getDevice().getFirmwareVersion() == null && XiaomiSppSupport.this.getCachedFirmwareVersion() != null) {
                getDevice().setFirmwareVersion(XiaomiSppSupport.this.getCachedFirmwareVersion());
            }

            builder.add(new nodomain.freeyourgadget.gadgetbridge.service.btbr.actions.SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));
            XiaomiSppSupport.this.getAuthService().startEncryptedHandshake(XiaomiSppSupport.this, builder);

            return builder;
        }

        @Override
        protected UUID getSupportedService() {
            return XiaomiBleUuids.UUID_SERVICE_SERIAL_PORT_PROFILE;
        }
    };

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private final AtomicInteger encryptionCounter = new AtomicInteger(0);

    public XiaomiSppSupport() {
        super.setConnectionSpecificSupport(this);
    }

    @Override
    public boolean connect() {
        return commsSupport.connect();
    }

    @Override
    public void setContext(GBDevice device, BluetoothAdapter adapter, Context context) {
        super.setContext(device, adapter, context);
        this.commsSupport.setContext(device, adapter, context);
    }

    private int findNextPossiblePreamble(final byte[] haystack) {
        for (int i = 0; i + 2 < haystack.length; i++) {
            // check if first byte matches
            if (haystack[i] == PACKET_PREAMBLE[0]) {
                return i;
            }
        }

        // did not find preamble
        return -1;
    }

    private void processBuffer() {
        // wait until at least an empty packet is in the buffer
        while (buffer.size() >= 11) {
            // start preamble compare
            byte[] bufferState = buffer.toByteArray();
            ByteBuffer headerBuffer = ByteBuffer.wrap(bufferState, 0, 7).order(ByteOrder.LITTLE_ENDIAN);
            byte[] preamble = new byte[PACKET_PREAMBLE.length];
            headerBuffer.get(preamble);

            if (!Arrays.equals(PACKET_PREAMBLE, preamble)) {
                int preambleOffset = findNextPossiblePreamble(bufferState);

                if (preambleOffset == -1) {
                    LOG.debug("Buffer did not contain a valid (start of) preamble, resetting");
                    buffer.reset();
                } else {
                    LOG.debug("Found possible preamble at offset {}, dumping preceeding bytes", preambleOffset);
                    byte[] remaining = new byte[bufferState.length - preambleOffset];
                    System.arraycopy(bufferState, preambleOffset, remaining, 0, remaining.length);
                    buffer.reset();
                    try {
                        buffer.write(remaining);
                    } catch (IOException ex) {
                        LOG.error("Failed to write bytes from found preamble offset back to buffer: ", ex);
                    }
                }

                return;
            }

            headerBuffer.getShort(); // skip flags and channel ID
            int payloadSize = headerBuffer.getShort() & 0xffff;
            int packetSize = payloadSize + 8; // payload size includes payload header

            if (bufferState.length < packetSize) {
                LOG.debug("Packet buffer not yet satisfied: buffer size {} < expected packet size {}", bufferState.length, packetSize);
                return;
            }

            LOG.debug("Full packet in buffer (buffer size: {}, packet size: {})", bufferState.length, packetSize);
            XiaomiSppPacket receivedPacket = XiaomiSppPacket.decode(bufferState); // remaining bytes unaffected

            onPacketReceived(receivedPacket);

            // extract remaining bytes from buffer
            byte[] remaining = new byte[bufferState.length - packetSize];
            System.arraycopy(bufferState, packetSize, remaining, 0, remaining.length);

            buffer.reset();

            try {
                buffer.write(remaining);
            } catch (IOException ex) {
                LOG.error("Failed to write remaining packet bytes back to buffer: ", ex);
            }
        }
    }

    @Override
    public void dispose() {
        commsSupport.dispose();
    }

    public void onSocketRead(byte[] data) {
        try {
            buffer.write(data);
        } catch (IOException ex) {
            LOG.error("Exception while writing buffer: ", ex);
        }

        processBuffer();
    }

    private void onPacketReceived(final XiaomiSppPacket packet) {
        if (packet == null) {
            // likely failed to parse the packet
            LOG.warn("Received null packet, did we fail to decode?");
            return;
        }

        LOG.debug("Packet received: {}", packet);

        byte[] payload = packet.getPayload();

        if (packet.getDataType() == 1) {
            payload = getAuthService().decrypt(payload);
        }

        if (packet.getChannel() == CHANNEL_PROTO_RX || packet.getChannel() == CHANNEL_PROTO_TX) {
            handleCommandBytes(payload);
        }

        if (packet.getChannel() == CHANNEL_FITNESS) {
            getHealthService().getActivityFetcher().addChunk(payload);
        }
    }

    @Override
    public void sendCommand(String taskName, XiaomiProto.Command command) {
        XiaomiSppPacket packet = XiaomiSppPacket.fromXiaomiCommand(command, getAuthService(), frameCounter.getAndIncrement(), false);
        LOG.debug("sending packet: {}", packet);
        TransactionBuilder builder = this.commsSupport.createTransactionBuilder("send " + taskName);
        builder.write(packet.encode(encryptionCounter));
        builder.queue(this.commsSupport.getQueue());
    }

    public void sendCommand(final TransactionBuilder builder, final XiaomiProto.Command command) {
        XiaomiSppPacket packet = XiaomiSppPacket.fromXiaomiCommand(command, getAuthService(), frameCounter.getAndIncrement(), false);
        LOG.debug("sending packet: {}", packet);

        builder.write(packet.encode(encryptionCounter));
        // do not queue here, that's the job of the caller
    }
}
