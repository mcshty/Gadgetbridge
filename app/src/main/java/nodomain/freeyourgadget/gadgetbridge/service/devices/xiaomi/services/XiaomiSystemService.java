/*  Copyright (C) 2023 José Rebelo, Yoran Vulker

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsUtils;
import nodomain.freeyourgadget.gadgetbridge.capabilities.password.PasswordCapabilityImpl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventSleepStateDetection;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventWearState;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiFWHelper;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.SleepState;
import nodomain.freeyourgadget.gadgetbridge.model.WearingState;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetProgressAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiVibrationPatternNotificationType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiPreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class XiaomiSystemService extends AbstractXiaomiService implements XiaomiDataUploadService.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiSystemService.class);

    // We persist the settings code when receiving the display items,
    // so we can enforce it when sending them
    private static final String PREF_SETTINGS_DISPLAY_ITEM_CODE = "xiaomi_settings_display_item_code";

    public static final int COMMAND_TYPE = 2;

    public static final int CMD_BATTERY = 1;
    public static final int CMD_DEVICE_INFO = 2;
    public static final int CMD_CLOCK = 3;
    public static final int CMD_FIRMWARE_INSTALL = 5;
    public static final int CMD_LANGUAGE = 6;
    public static final int CMD_PASSWORD_GET = 9;
    public static final int CMD_FIND_PHONE = 17;
    public static final int CMD_FIND_WATCH = 18;
    public static final int CMD_PASSWORD_SET = 21;
    public static final int CMD_DISPLAY_ITEMS_GET = 29;
    public static final int CMD_DISPLAY_ITEMS_SET = 30;
    public static final int CMD_VIBRATION_PATTERNS_GET = 46;
    public static final int CMD_VIBRATION_PATTERNS_SET = 47;
    public static final int CMD_VIBRATION_PATTERNS_DEL = 61;
    public static final int CMD_DEVICE_STATE_GET = 78;
    public static final int CMD_DEVICE_STATE = 79;

    // Not null if we're installing a firmware
    private XiaomiFWHelper fwHelper = null;
    private WearingState currentWearingState = WearingState.UNKNOWN;
    private BatteryState currentBatteryState = BatteryState.UNKNOWN;
    private SleepState currentSleepDetectionState = SleepState.UNKNOWN;

    public XiaomiSystemService(final XiaomiSupport support) {
        super(support);
    }

    @Override
    public void initialize() {
        // Request device info and configs
        getSupport().sendCommand("get device info", COMMAND_TYPE, CMD_DEVICE_INFO);
        getSupport().sendCommand("get device status", COMMAND_TYPE, CMD_DEVICE_STATE_GET);
        // device status request may initialize wearing, charger, sleeping, and activity state, so
        // get battery level as a failsafe for devices that don't support CMD_DEVICE_STATE_SET command
        getSupport().sendCommand("get battery state", COMMAND_TYPE, CMD_BATTERY);
        getSupport().sendCommand("get password", COMMAND_TYPE, CMD_PASSWORD_GET);
        getSupport().sendCommand("get display items", COMMAND_TYPE, CMD_DISPLAY_ITEMS_GET);
        getSupport().sendCommand("get vibration patterns", COMMAND_TYPE, CMD_VIBRATION_PATTERNS_GET);
    }

    @Override
    public void handleCommand(final XiaomiProto.Command cmd) {
        switch (cmd.getSubtype()) {
            case CMD_DEVICE_INFO:
                handleDeviceInfo(cmd.getSystem().getDeviceInfo());
                return;
            case CMD_BATTERY:
                handleBattery(cmd.getSystem().getPower().getBattery());
                return;
            case CMD_FIRMWARE_INSTALL:
                final int installStatus = cmd.getSystem().getFirmwareInstallResponse().getStatus();
                if (installStatus != 0) {
                    LOG.warn("Invalid firmware install status {} for {}", installStatus, fwHelper.getId());
                    return;
                }

                LOG.debug("Firmware install status 0, uploading");
                setDeviceBusy();
                getSupport().getDataUploader().setCallback(this);
                getSupport().getDataUploader().requestUpload(XiaomiDataUploadService.TYPE_FIRMWARE, fwHelper.getBytes());
                return;
            case CMD_PASSWORD_GET:
                handlePassword(cmd.getSystem().getPassword());
                return;
            case CMD_FIND_PHONE:
                LOG.debug("Got find phone: {}", cmd.getSystem().getFindDevice());
                final GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();
                if (cmd.getSystem().getFindDevice() == 0) {
                    findPhoneEvent.event = GBDeviceEventFindPhone.Event.START;
                } else {
                    findPhoneEvent.event = GBDeviceEventFindPhone.Event.STOP;
                }
                getSupport().evaluateGBDeviceEvent(findPhoneEvent);
                return;
            case CMD_DISPLAY_ITEMS_GET:
                handleDisplayItems(cmd.getSystem().getDisplayItems());
                return;
            case CMD_VIBRATION_PATTERNS_GET:
                handleVibrationPatterns(cmd.getSystem().getVibrationPatterns());
                return;
            case CMD_DEVICE_STATE_GET:
                handleBasicDeviceState(cmd.getSystem().hasBasicDeviceState()
                        ? cmd.getSystem().getBasicDeviceState()
                        : null);
                return;
            case CMD_DEVICE_STATE:
                handleDeviceState(cmd.getSystem().hasDeviceState()
                        ? cmd.getSystem().getDeviceState()
                        : null);
                return;
        }

        LOG.warn("Unknown system command {}", cmd.getSubtype());
    }

    @Override
    public boolean onSendConfiguration(final String config, final Prefs prefs) {
        switch (config) {
            case DeviceSettingsPreferenceConst.PREF_LANGUAGE:
                setLanguage();
                return true;
            case DeviceSettingsPreferenceConst.PREF_TIMEFORMAT:
                setCurrentTime();
                return true;
            case PasswordCapabilityImpl.PREF_PASSWORD_ENABLED:
            case PasswordCapabilityImpl.PREF_PASSWORD:
                setPassword();
                return true;
            case HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE:
                setDisplayItems();
                return true;
        }

        return super.onSendConfiguration(config, prefs);
    }

    public void setLanguage() {
        String localeString = GBApplication.getDeviceSpecificSharedPrefs(getSupport().getDevice().getAddress()).getString(
                DeviceSettingsPreferenceConst.PREF_LANGUAGE, DeviceSettingsPreferenceConst.PREF_LANGUAGE_AUTO
        );
        if (DeviceSettingsPreferenceConst.PREF_LANGUAGE_AUTO.equals(localeString)) {
            String language = Locale.getDefault().getLanguage();
            String country = Locale.getDefault().getCountry();

            if (StringUtils.isNullOrEmpty(country)) {
                // sometimes country is null, no idea why, guess it.
                country = language;
            }
            localeString = language + "_" + country.toUpperCase();
        }

        LOG.info("Set language: {}", localeString);

        getSupport().sendCommand(
                "set language",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_LANGUAGE)
                        .setSystem(XiaomiProto.System.newBuilder().setLanguage(
                                XiaomiProto.Language.newBuilder().setCode(localeString.toLowerCase(Locale.ROOT))
                        ))
                        .build()
        );
    }

    public void setCurrentTime() {
        LOG.debug("Setting current time");

        final Calendar now = GregorianCalendar.getInstance();
        final TimeZone tz = TimeZone.getDefault();

        final GBPrefs gbPrefs = new GBPrefs(new Prefs(GBApplication.getDeviceSpecificSharedPrefs(getSupport().getDevice().getAddress())));
        final String timeFormat = gbPrefs.getTimeFormat();
        final boolean is24hour = DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_24H.equals(timeFormat);

        final XiaomiProto.Clock clock = XiaomiProto.Clock.newBuilder()
                .setTime(XiaomiProto.Time.newBuilder()
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setSecond(now.get(Calendar.SECOND))
                        .setMillisecond(now.get(Calendar.MILLISECOND))
                        .build())
                .setDate(XiaomiProto.Date.newBuilder()
                        .setYear(now.get(Calendar.YEAR))
                        .setMonth(now.get(Calendar.MONTH) + 1)
                        .setDay(now.get(Calendar.DATE))
                        .build())
                .setTimezone(XiaomiProto.TimeZone.newBuilder()
                        .setZoneOffset(((now.get(Calendar.ZONE_OFFSET) / 1000) / 60) / 15)
                        .setDstOffset(((now.get(Calendar.DST_OFFSET) / 1000) / 60) / 15)
                        .setName(tz.getID())
                        .build())
                .setIsNot24Hour(!is24hour)
                .build();

        getSupport().sendCommand(
                "set time",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_CLOCK)
                        .setSystem(XiaomiProto.System.newBuilder().setClock(clock).build())
                        .build()
        );
    }

    private void handleDeviceInfo(final XiaomiProto.DeviceInfo deviceInfo) {
        LOG.debug("Got device info: fw={} hw={} sn={}", deviceInfo.getFirmware(), deviceInfo.getModel(), deviceInfo.getSerialNumber());

        final GBDeviceEventVersionInfo gbDeviceEventVersionInfo = new GBDeviceEventVersionInfo();
        gbDeviceEventVersionInfo.fwVersion = deviceInfo.getFirmware();
        //gbDeviceEventVersionInfo.fwVersion2 = "N/A";
        gbDeviceEventVersionInfo.hwVersion = deviceInfo.getModel();
        final GBDeviceEventUpdateDeviceInfo gbDeviceEventUpdateDeviceInfo = new GBDeviceEventUpdateDeviceInfo("SERIAL: ", deviceInfo.getSerialNumber());

        getSupport().evaluateGBDeviceEvent(gbDeviceEventVersionInfo);
        getSupport().evaluateGBDeviceEvent(gbDeviceEventUpdateDeviceInfo);
    }

    private BatteryState convertBatteryStateFromRawValue(int chargerState) {
        switch (chargerState) {
            case 1:
                return BatteryState.BATTERY_CHARGING;
            case 2:
                return BatteryState.BATTERY_NORMAL;
        }

        return BatteryState.UNKNOWN;
    }

    private void handleBattery(final XiaomiProto.Battery battery) {
        LOG.debug("Got battery: {}", battery.getLevel());

        final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();
        batteryInfo.batteryIndex = 0;
        batteryInfo.level = battery.getLevel();

        // currentBatteryState may already be set if the DeviceState message contained the field,
        // but since some models report their charger state through this message, we will update it
        // from here
        if (battery.hasState()) {
            currentBatteryState = convertBatteryStateFromRawValue(battery.getState());

            if (currentBatteryState == BatteryState.UNKNOWN) {
                LOG.warn("Unknown battery state {}", battery.getState());
            }
        }

        batteryInfo.state = currentBatteryState;
        getSupport().evaluateGBDeviceEvent(batteryInfo);
    }

    private void setPassword() {
        final Prefs prefs = getDevicePrefs();

        final boolean passwordEnabled = prefs.getBoolean(PasswordCapabilityImpl.PREF_PASSWORD_ENABLED, false);
        final String password = prefs.getString(PasswordCapabilityImpl.PREF_PASSWORD, null);

        LOG.info("Setting password: {}, {}", passwordEnabled, password);

        if (password == null || password.isEmpty()) {
            LOG.warn("Invalid password: {}", password);
            return;
        }

        final XiaomiProto.Password.Builder passwordBuilder = XiaomiProto.Password.newBuilder()
                .setState(passwordEnabled ? 2 : 1)
                .setPassword(password);

        getSupport().sendCommand(
                "set password",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_PASSWORD_SET)
                        .setSystem(XiaomiProto.System.newBuilder().setPassword(passwordBuilder).build())
                        .build()
        );
    }

    private void handlePassword(final XiaomiProto.Password password) {
        LOG.debug("Got device password");
        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences(
                PasswordCapabilityImpl.PREF_PASSWORD_ENABLED,
                password.getState() == 2
        );
        if (password.hasPassword()) {
            eventUpdatePreferences.withPreference(
                    PasswordCapabilityImpl.PREF_PASSWORD,
                    password.getPassword()
            );
        }
        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void setDisplayItems() {
        final Prefs prefs = getDevicePrefs();
        final List<String> allScreens = new ArrayList<>(prefs.getList(DeviceSettingsUtils.getPrefPossibleValuesKey(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE), Collections.emptyList()));
        final List<String> allLabels = new ArrayList<>(prefs.getList(DeviceSettingsUtils.getPrefPossibleValueLabelsKey(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE), Collections.emptyList()));
        final List<String> enabledScreens = new ArrayList<>(prefs.getList(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE, Collections.emptyList()));
        final String settingsCode = prefs.getString(PREF_SETTINGS_DISPLAY_ITEM_CODE, null);

        if (allScreens.isEmpty()) {
            LOG.warn("No list of all screens");
            return;
        }

        if (allScreens.size() != allLabels.size()) {
            LOG.warn(
                    "Mismatched allScreens ({}) and allLabels ({}) sizes - this should never happen",
                    allScreens.size(),
                    allLabels.size()
            );
            return;
        }

        final Map<String, String> labelsMap = new HashMap<>();
        for (int i = 0; i < allScreens.size(); i++) {
            labelsMap.put(allScreens.get(i), allLabels.get(i));
        }

        LOG.debug("Setting display items: {}", enabledScreens);

        if (settingsCode != null && !enabledScreens.contains(settingsCode)) {
            enabledScreens.add(settingsCode);
        }

        boolean inMoreSection = false;
        final XiaomiProto.DisplayItems.Builder displayItems = XiaomiProto.DisplayItems.newBuilder();
        for (final String enabledScreen : enabledScreens) {
            if (enabledScreen.equals("more")) {
                inMoreSection = true;
                continue;
            }
            if (labelsMap.get(enabledScreen) == null) {
                continue;
            }

            final XiaomiProto.DisplayItem.Builder displayItem = XiaomiProto.DisplayItem.newBuilder()
                    .setCode(enabledScreen)
                    .setName(labelsMap.get(enabledScreen))
                    .setUnknown5(1);

            if (inMoreSection) {
                displayItem.setInMoreSection(true);
            }

            if (enabledScreen.equals(settingsCode)) {
                displayItem.setIsSettings(1);
            }

            displayItems.addDisplayItem(displayItem);
        }

        for (final String screen : allScreens) {
            if (enabledScreens.contains(screen)) {
                continue;
            }

            final XiaomiProto.DisplayItem.Builder displayItem = XiaomiProto.DisplayItem.newBuilder()
                    .setCode(screen)
                    .setName(labelsMap.get(screen))
                    .setDisabled(true)
                    .setUnknown5(1);

            displayItems.addDisplayItem(displayItem);
        }

        getSupport().sendCommand(
                "set display items",
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_DISPLAY_ITEMS_SET)
                        .setSystem(XiaomiProto.System.newBuilder().setDisplayItems(displayItems))
                        .build()
        );
    }

    private void handleDisplayItems(final XiaomiProto.DisplayItems displayItems) {
        LOG.debug("Got {} display items", displayItems.getDisplayItemCount());

        final List<String> allScreens = new ArrayList<>();
        final List<String> allScreensLabels = new ArrayList<>();
        final List<String> mainScreens = new ArrayList<>();
        final List<String> moreScreens = new ArrayList<>();
        String settingsCode = null;
        for (final XiaomiProto.DisplayItem displayItem : displayItems.getDisplayItemList()) {
            allScreens.add(displayItem.getCode());
            allScreensLabels.add(displayItem.getName().replace(",", ""));
            if (!displayItem.getDisabled()) {
                if (displayItem.getInMoreSection()) {
                    moreScreens.add(displayItem.getCode());
                } else {
                    mainScreens.add(displayItem.getCode());
                }
            }

            if (displayItem.getIsSettings() == 1) {
                settingsCode = displayItem.getCode();
            }
        }

        final List<String> enabledScreens = new ArrayList<>(mainScreens);
        if (!moreScreens.isEmpty()) {
            enabledScreens.add("more");
            enabledScreens.addAll(moreScreens);
        }

        allScreens.add("more");
        allScreensLabels.add(getSupport().getContext().getString(R.string.menuitem_more));

        final String allScreensPrefValue = StringUtils.join(",", allScreens.toArray(new String[0])).toString();
        final String allScreensLabelsPrefValue = StringUtils.join(",", allScreensLabels.toArray(new String[0])).toString();
        final String prefValue = StringUtils.join(",", enabledScreens.toArray(new String[0])).toString();

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences()
                .withPreference(DeviceSettingsUtils.getPrefPossibleValuesKey(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE), allScreensPrefValue)
                .withPreference(DeviceSettingsUtils.getPrefPossibleValueLabelsKey(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE), allScreensLabelsPrefValue)
                .withPreference(PREF_SETTINGS_DISPLAY_ITEM_CODE, settingsCode)
                .withPreference(HuamiConst.PREF_DISPLAY_ITEMS_SORTABLE, prefValue);

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void handleVibrationPatterns(final XiaomiProto.VibrationPatterns vibrationPatterns) {
        LOG.debug("Got {} vibration pattern notification types", vibrationPatterns.getNotificationTypeCount());

        final GBDeviceEventUpdatePreferences eventUpdatePreferences = new GBDeviceEventUpdatePreferences();

        final Set<String> notificationTypesPrefValue = new HashSet<>();

        for (final XiaomiProto.VibrationNotificationType notificationType : vibrationPatterns.getNotificationTypeList()) {
            final HuamiVibrationPatternNotificationType vibrationPatternNotificationType;
            switch (notificationType.getNotificationType()) {
                case 1:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.INCOMING_CALL;
                    break;
                case 2: // TODO confirm which one is events, which one is schedule
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.EVENT_REMINDER;
                    break;
                case 3:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.ALARM;
                    break;
                case 4:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.APP_ALERTS;
                    break;
                case 5:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.IDLE_ALERTS;
                    break;
                case 6:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.INCOMING_SMS;
                    break;
                case 7:
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.GOAL_NOTIFICATION;
                    break;
                case 8: // TODO confirm which one is events, which one is schedule
                    vibrationPatternNotificationType = HuamiVibrationPatternNotificationType.SCHEDULE;
                    break;
                default:
                    LOG.warn("Unknown vibration pattern notification type {}", notificationType.getNotificationType());
                    continue;
            }

            final String nameLowercase = vibrationPatternNotificationType.name().toLowerCase(Locale.ROOT);
            notificationTypesPrefValue.add(nameLowercase);

            eventUpdatePreferences.withPreference(
                    HuamiConst.PREF_HUAMI_VIBRATION_PROFILE_PREFIX + nameLowercase,
                    String.valueOf(notificationType.getPreset())
            );
        }

        eventUpdatePreferences.withPreference(
                XiaomiPreferences.PREF_VIBRATION_PATTERN_NOTIFICATION_TYPES,
                notificationTypesPrefValue
        );

        final List<String> customPatternIds = new ArrayList<>();
        final List<String> customPatternNames = new ArrayList<>();

        for (final XiaomiProto.CustomVibrationPattern customPattern : vibrationPatterns.getCustomVibrationPatternList()) {
            customPatternIds.add(String.valueOf(customPattern.getId()));
            customPatternNames.add(customPattern.getName().replace(",", ""));
        }

        eventUpdatePreferences.withPreference(
                XiaomiPreferences.PREF_VIBRATION_PATTERN_IDS,
                StringUtils.join(",", customPatternIds.toArray(new String[0])).toString()
        );

        eventUpdatePreferences.withPreference(
                XiaomiPreferences.PREF_VIBRATION_PATTERN_NAMES,
                StringUtils.join(",", customPatternNames.toArray(new String[0])).toString()
        );

        getSupport().evaluateGBDeviceEvent(eventUpdatePreferences);
    }

    private void handleWearingState(int newStateValue) {
        WearingState newState;

        switch (newStateValue) {
            case 1:
                newState = WearingState.WEARING;
                break;
            case 2:
                newState = WearingState.NOT_WEARING;
                break;
            default:
                LOG.warn("Unknown wearing state {}", newStateValue);
                return;
        }

        LOG.debug("Current wearing state = {}, new wearing state = {}", currentWearingState, newState);

        if (currentWearingState != WearingState.UNKNOWN && currentWearingState != newState) {
            GBDeviceEventWearState event = new GBDeviceEventWearState();
            event.wearingState = newState;
            getSupport().evaluateGBDeviceEvent(event);
        }

        currentWearingState = newState;
    }

    private void handleSleepDetectionState(int newStateValue) {
        SleepState newState;

        switch (newStateValue) {
            case 1:
                newState = SleepState.ASLEEP;
                break;
            case 2:
                newState = SleepState.AWAKE;
                break;
            default:
                LOG.warn("Unknown sleep detection state {}", newStateValue);
                return;
        }

        LOG.debug("Current sleep detection state = {}, new sleep detection state = {}", currentSleepDetectionState, newState);

        if (currentSleepDetectionState != SleepState.UNKNOWN && currentSleepDetectionState != newState) {
            GBDeviceEventSleepStateDetection event = new GBDeviceEventSleepStateDetection();
            event.sleepState = newState;
            getSupport().evaluateGBDeviceEvent(event);
        }

        currentSleepDetectionState = newState;
    }

    public void handleBasicDeviceState(XiaomiProto.BasicDeviceState deviceState) {
        LOG.debug("Got basic device state: {}", deviceState);

        if (null == deviceState) {
            LOG.warn("Got null for BasicDeviceState, requesting battery state and returning");
            getSupport().sendCommand("request battery state", COMMAND_TYPE, CMD_BATTERY);
            return;
        }

        // handle battery info from message
        {
            BatteryState newBatteryState = deviceState.getIsCharging() ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
            LOG.debug("Previous charging state: {}, new charging state: {}", currentBatteryState, newBatteryState);

            currentBatteryState = newBatteryState;

            // if the device state did not have a battery level, request it from the device through other means.
            // the battery state is now cached, so that it can be used when another response with battery level is received.
            if (!deviceState.hasBatteryLevel()) {
                getSupport().sendCommand("request battery state", COMMAND_TYPE, CMD_BATTERY);
            } else {
                GBDeviceEventBatteryInfo event = new GBDeviceEventBatteryInfo();
                event.batteryIndex = 0;
                event.state = newBatteryState;
                event.level = deviceState.getBatteryLevel();
                getSupport().evaluateGBDeviceEvent(event);
            }
        }

        // handle sleep state from message
        {
            SleepState newSleepState = deviceState.getIsUserAsleep() ? SleepState.ASLEEP : SleepState.AWAKE;
            LOG.debug("Previous sleep state: {}, new sleep state: {}", currentSleepDetectionState, newSleepState);

            // send event if the previous state is known and the new state is different from cached
            if (currentSleepDetectionState != SleepState.UNKNOWN && currentSleepDetectionState != newSleepState) {
                GBDeviceEventSleepStateDetection event = new GBDeviceEventSleepStateDetection();
                event.sleepState = newSleepState;
                getSupport().evaluateGBDeviceEvent(event);
            }

            currentSleepDetectionState = newSleepState;
        }

        // handle wearing state from message
        {
            WearingState newWearingState = deviceState.getIsWorn() ? WearingState.WEARING : WearingState.NOT_WEARING;
            LOG.debug("Previous wearing state: {}, new wearing state: {}", currentWearingState, newWearingState);

            if (currentWearingState != WearingState.UNKNOWN && currentWearingState != newWearingState) {
                GBDeviceEventWearState event = new GBDeviceEventWearState();
                event.wearingState = newWearingState;
                getSupport().evaluateGBDeviceEvent(event);
            }

            currentWearingState = newWearingState;
        }

        // TODO: handle activity state
    }

    public void handleDeviceState(XiaomiProto.DeviceState deviceState) {
        LOG.debug("Got device state: {}", deviceState);

        if (null == deviceState) {
            LOG.warn("Got null for DeviceState, requesting battery state and returning");
            getSupport().sendCommand("request battery state", COMMAND_TYPE, CMD_BATTERY);
            return;
        }

        if (deviceState.hasWearingState()) {
            handleWearingState(deviceState.getWearingState());
        }

        // The charger state of some devices can only be known when listening for device status
        // updates. If available, this state will be cached here and updated in the GBDevice upon
        // the next retrieval of the battery level
        if (deviceState.hasChargingState()) {
            BatteryState newBatteryState = convertBatteryStateFromRawValue(deviceState.getChargingState());

            LOG.debug("Current battery state = {}, new battery state = {}", currentBatteryState, newBatteryState);

            if (currentBatteryState != newBatteryState) {
                currentBatteryState = newBatteryState;
            }
        }

        if (deviceState.hasSleepState()) {
            handleSleepDetectionState(deviceState.getSleepState());
        }

        // TODO process warning (unknown possible values) and activity information

        // request battery state to request battery level and charger state on supported models
        getSupport().sendCommand("request battery state", COMMAND_TYPE, CMD_BATTERY);
    }

    public void onFindPhone(final boolean start) {
        LOG.debug("Find phone: {}", start);

        if (!start) {
            // Stop on watch
            getSupport().sendCommand(
                    "find phone stop",
                    XiaomiProto.Command.newBuilder()
                            .setType(COMMAND_TYPE)
                            .setSubtype(CMD_FIND_PHONE)
                            .setSystem(XiaomiProto.System.newBuilder().setFindDevice(1).build())
                            .build()
            );
        }
    }

    public void onFindWatch(final boolean start) {
        LOG.debug("Find watch: {}", start);

        getSupport().sendCommand(
                "find watch " + start,
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_FIND_WATCH)
                        .setSystem(XiaomiProto.System.newBuilder().setFindDevice(start ? 0 : 1).build())
                        .build()
        );
    }

    public void installFirmware(final XiaomiFWHelper fwHelper) {
        assert fwHelper.isValid();
        assert fwHelper.isFirmware();

        this.fwHelper = fwHelper;

        getSupport().sendCommand(
                "install firmware " + fwHelper.getVersion(),
                XiaomiProto.Command.newBuilder()
                        .setType(COMMAND_TYPE)
                        .setSubtype(CMD_FIRMWARE_INSTALL)
                        .setSystem(XiaomiProto.System.newBuilder().setFirmwareInstallRequest(
                                XiaomiProto.FirmwareInstallRequest.newBuilder()
                                        .setUnknown1(0)
                                        .setUnknown2(0)
                                        .setVersion(fwHelper.getVersion())
                                        .setMd5(GB.hexdump(CheckSums.md5(fwHelper.getBytes())).toLowerCase(Locale.ROOT))
                        ))
                        .build()
        );
    }

    private void setDeviceBusy() {
        final GBDevice device = getSupport().getDevice();
        device.setBusyTask(getSupport().getContext().getString(R.string.updating_firmware));
        device.sendDeviceUpdateIntent(getSupport().getContext());
    }

    private void unsetDeviceBusy() {
        final GBDevice device = getSupport().getDevice();
        if (device != null && device.isConnected()) {
            if (device.isBusy()) {
                device.unsetBusyTask();
                device.sendDeviceUpdateIntent(getSupport().getContext());
            }
            device.sendDeviceUpdateIntent(getSupport().getContext());
        }
    }

    @Override
    public void onUploadFinish(final boolean success) {
        LOG.debug("Firmware upload finished: {}", success);

        getSupport().getDataUploader().setCallback(null);

        final String notificationMessage = success ?
                getSupport().getContext().getString(R.string.updatefirmwareoperation_update_complete) :
                getSupport().getContext().getString(R.string.updatefirmwareoperation_write_failed);

        GB.updateInstallNotification(notificationMessage, false, 100, getSupport().getContext());

        unsetDeviceBusy();

        fwHelper = null;
    }

    @Override
    public void onUploadProgress(final int progressPercent) {
        try {
            final TransactionBuilder builder = getSupport().createTransactionBuilder("send data upload progress");
            builder.add(new SetProgressAction(
                    getSupport().getContext().getString(R.string.updatefirmwareoperation_update_in_progress),
                    true,
                    progressPercent,
                    getSupport().getContext()
            ));
            builder.queue(getSupport().getQueue());
        } catch (final Exception e) {
            LOG.error("Failed to update progress notification", e);
        }
    }
}
