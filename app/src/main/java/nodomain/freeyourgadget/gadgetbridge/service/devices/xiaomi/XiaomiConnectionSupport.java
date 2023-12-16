package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;

public abstract class XiaomiConnectionSupport {
    public abstract boolean connect();
    public abstract void onUploadProgress(int textRsrc, int progressPercent);
    public abstract void dispose();
    public abstract void setContext(final GBDevice device, final BluetoothAdapter adapter, final Context context);
    public abstract void disconnect();
    public abstract void sendCommand(final String taskName, final XiaomiProto.Command command);
}
