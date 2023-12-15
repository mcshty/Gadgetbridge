package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watchs1pro;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiCoordinator;

public class XiaomiWatchS1ProCoordinator extends XiaomiCoordinator {

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO confirm that the second device name is actually in use somewhere
        return Pattern.compile("^(Xiaomi Watch S1 Pro [0-9A-F]{4}|L61.*[0-9A-F])$");
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.BOTH;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_xiaomi_watch_s1_pro;
    }

    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miwatch;
    }

    @Override
    public int getDisabledIconResource() {
        return R.drawable.ic_device_miwatch_disabled;
    }
}
