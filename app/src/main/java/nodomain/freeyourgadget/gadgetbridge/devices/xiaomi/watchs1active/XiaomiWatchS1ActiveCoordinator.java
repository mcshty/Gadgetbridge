/*  Copyright (C) 2023 Yoran Vulker

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watchs1active;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiInstallHandler;

public class XiaomiWatchS1ActiveCoordinator extends XiaomiCoordinator {

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_xiaomi_watch_s1_active;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^XiaomiWatchS1Active [0-9A-Z]{4}$");
    }

    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.BOTH;
    }

    @Override
    public boolean supportsFindDevice() {
        return false;
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(Uri uri, Context context) {
        final XiaomiInstallHandler handler = new XiaomiInstallHandler(uri, context);

        return handler.isValid() ? handler : null;
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
    public boolean supportsMultipleWeatherLocations() {
        return true;
    }
}
