/*  Copyright (C) 2017-2021 Andreas Shimokawa, Daniele Gobbetti, Dmytro Bielik

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.amazfittrexpro;

import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiFirmwareInfo;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiFirmwareType;
import nodomain.freeyourgadget.gadgetbridge.util.ArrayUtils;

public class AmazfitTRexProFirmwareInfo extends HuamiFirmwareInfo {
    private static final int FW_OFFSET = 3;

    private static final byte[] FW_HEADER = new byte[]{
            0x20, (byte) 0x99, 0x12, 0x01, 0x08 // completely nonsense probably
    };

    // gps detection is totally bogus, just the first 16 bytes
    private static final byte[][] GPS_HEADERS = {
            new byte[]{
                    0x73, 0x75, 0x68, (byte) 0xd0, 0x70, 0x73, (byte) 0xbb, 0x5a,
                    0x3e, (byte) 0xc3, (byte) 0xd3, 0x09, (byte) 0x9e, 0x1d, (byte) 0xd3, (byte) 0xc9
            }
    };
    private static Map<Integer, String> crcToVersion = new HashMap<>();

    static {
        // firmware

        // Latin Firmware

        // resources

        // font

        // gps
    }

    public AmazfitTRexProFirmwareInfo(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected HuamiFirmwareType determineFirmwareType(byte[] bytes) {
        if (ArrayUtils.equals(bytes, NEWRES_HEADER, COMPRESSED_RES_HEADER_OFFSET_NEW)) {
            return HuamiFirmwareType.RES_COMPRESSED;
        }
        if (ArrayUtils.equals(bytes, FW_HEADER, FW_OFFSET)) {
            return HuamiFirmwareType.FIRMWARE;
        }

            return HuamiFirmwareType.INVALID;
        }
        if (ArrayUtils.startsWith(bytes, UIHH_HEADER) && (bytes[4] == 1 || bytes[4] == 2)) {
            return HuamiFirmwareType.WATCHFACE;
        }

        if (ArrayUtils.startsWith(bytes, NEWFT_HEADER)) {
            if (bytes[10] == 0x01) {
                return HuamiFirmwareType.FONT;
            } else if (bytes[10] == 0x02) {
                return HuamiFirmwareType.FONT_LATIN;
            }
        }

        if (ArrayUtils.startsWith(bytes, GPS_ALMANAC_HEADER)) {
            return HuamiFirmwareType.GPS_ALMANAC;
        }

        if (ArrayUtils.startsWith(bytes, GPS_CEP_HEADER)) {
            return HuamiFirmwareType.GPS_CEP;
        }

        if (ArrayUtils.startsWith(bytes, AGPS_UIHH_HEADER)) {
            return HuamiFirmwareType.AGPS_UIHH;
        }

        for (byte[] gpsHeader : GPS_HEADERS) {
            if (ArrayUtils.startsWith(bytes, gpsHeader)) {
                return HuamiFirmwareType.GPS;
            }
        }

        return HuamiFirmwareType.INVALID;
    }

    @Override
    public boolean isGenerallyCompatibleWith(GBDevice device) {
        return isHeaderValid() && device.getType() == DeviceType.AMAZFITTREXPRO;
    }

    @Override
    protected Map<Integer, String> getCrcMap() {
        return crcToVersion;
    }
}
