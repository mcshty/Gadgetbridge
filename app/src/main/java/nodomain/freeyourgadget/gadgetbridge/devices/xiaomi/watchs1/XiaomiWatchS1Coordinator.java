package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watchs1;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiCoordinator;

public class XiaomiWatchS1Coordinator extends XiaomiCoordinator {

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_xiaomi_watch_s1;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miwatch;
    }

    @Override
    public int getDisabledIconResource() {
        return R.drawable.ic_device_miwatch_disabled;
    }

    @Override
    public ConnectionType getConnectionType() {
        // TODO make sure that the device can actually communicate in BLE mode
        return ConnectionType.BOTH;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Xiaomi Watch S1 [0-9A-F]{4}$");
    }
}
