/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.satellite;

import static android.telephony.NetworkRegistrationInfo.FIRST_SERVICE_TYPE;
import static android.telephony.NetworkRegistrationInfo.LAST_SERVICE_TYPE;

import static java.util.stream.Collectors.joining;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.PersistableBundle;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILUtils;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utils class for satellite service <-> framework conversions
 */
public class SatelliteServiceUtils {
    private static final String TAG = "SatelliteServiceUtils";

    /**
     * Convert radio technology from service definition to framework definition.
     * @param radioTechnology The NTRadioTechnology from the satellite service.
     * @return The converted NTRadioTechnology for the framework.
     */
    @SatelliteManager.NTRadioTechnology
    public static int fromSatelliteRadioTechnology(int radioTechnology) {
        switch (radioTechnology) {
            case NTRadioTechnology.NB_IOT_NTN:
                return SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN;
            case NTRadioTechnology.NR_NTN:
                return SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN;
            case NTRadioTechnology.EMTC_NTN:
                return SatelliteManager.NT_RADIO_TECHNOLOGY_EMTC_NTN;
            case NTRadioTechnology.PROPRIETARY:
                return SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY;
            default:
                loge("Received invalid radio technology: " + radioTechnology);
                return SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN;
        }
    }

    /**
     * Convert satellite error from service definition to framework definition.
     * @param error The SatelliteError from the satellite service.
     * @return The converted SatelliteResult for the framework.
     */
    @SatelliteManager.SatelliteResult public static int fromSatelliteError(int error) {
        switch (error) {
            case SatelliteResult.SATELLITE_RESULT_SUCCESS:
                return SatelliteManager.SATELLITE_RESULT_SUCCESS;
            case SatelliteResult.SATELLITE_RESULT_ERROR:
                return SatelliteManager.SATELLITE_RESULT_ERROR;
            case SatelliteResult.SATELLITE_RESULT_SERVER_ERROR:
                return SatelliteManager.SATELLITE_RESULT_SERVER_ERROR;
            case SatelliteResult.SATELLITE_RESULT_SERVICE_ERROR:
                return SatelliteManager.SATELLITE_RESULT_SERVICE_ERROR;
            case SatelliteResult.SATELLITE_RESULT_MODEM_ERROR:
                return SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
            case SatelliteResult.SATELLITE_RESULT_NETWORK_ERROR:
                return SatelliteManager.SATELLITE_RESULT_NETWORK_ERROR;
            case SatelliteResult.SATELLITE_RESULT_INVALID_MODEM_STATE:
                return SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
            case SatelliteResult.SATELLITE_RESULT_INVALID_ARGUMENTS:
                return SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS;
            case SatelliteResult.SATELLITE_RESULT_REQUEST_FAILED:
                return SatelliteManager.SATELLITE_RESULT_REQUEST_FAILED;
            case SatelliteResult.SATELLITE_RESULT_RADIO_NOT_AVAILABLE:
                return SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
            case SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED:
                return SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
            case SatelliteResult.SATELLITE_RESULT_NO_RESOURCES:
                return SatelliteManager.SATELLITE_RESULT_NO_RESOURCES;
            case SatelliteResult.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED:
                return SatelliteManager.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED;
            case SatelliteResult.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS:
                return SatelliteManager.SATELLITE_RESULT_SERVICE_PROVISION_IN_PROGRESS;
            case SatelliteResult.SATELLITE_RESULT_REQUEST_ABORTED:
                return SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED;
            case SatelliteResult.SATELLITE_RESULT_ACCESS_BARRED:
                return SatelliteManager.SATELLITE_RESULT_ACCESS_BARRED;
            case SatelliteResult.SATELLITE_RESULT_NETWORK_TIMEOUT:
                return SatelliteManager.SATELLITE_RESULT_NETWORK_TIMEOUT;
            case SatelliteResult.SATELLITE_RESULT_NOT_REACHABLE:
                return SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE;
            case SatelliteResult.SATELLITE_RESULT_NOT_AUTHORIZED:
                return SatelliteManager.SATELLITE_RESULT_NOT_AUTHORIZED;
        }
        loge("Received invalid satellite service error: " + error);
        return SatelliteManager.SATELLITE_RESULT_SERVICE_ERROR;
    }

    /**
     * Convert satellite modem state from service definition to framework definition.
     * @param modemState The SatelliteModemState from the satellite service.
     * @return The converted SatelliteModemState for the framework.
     */
    @SatelliteManager.SatelliteModemState
    public static int fromSatelliteModemState(int modemState) {
        switch (modemState) {
            case SatelliteModemState.SATELLITE_MODEM_STATE_IDLE:
                return SatelliteManager.SATELLITE_MODEM_STATE_IDLE;
            case SatelliteModemState.SATELLITE_MODEM_STATE_LISTENING:
                return SatelliteManager.SATELLITE_MODEM_STATE_LISTENING;
            case SatelliteModemState.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING:
                return SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING;
            case SatelliteModemState.SATELLITE_MODEM_STATE_DATAGRAM_RETRYING:
                return SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_RETRYING;
            case SatelliteModemState.SATELLITE_MODEM_STATE_OFF:
                return SatelliteManager.SATELLITE_MODEM_STATE_OFF;
            case SatelliteModemState.SATELLITE_MODEM_STATE_UNAVAILABLE:
                return SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE;
            case SatelliteModemState.SATELLITE_MODEM_STATE_NOT_CONNECTED:
                return SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED;
            case SatelliteModemState.SATELLITE_MODEM_STATE_CONNECTED:
                return SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED;
            default:
                loge("Received invalid modem state: " + modemState);
                return SatelliteManager.SATELLITE_MODEM_STATE_UNKNOWN;
        }
    }

    /**
     * Convert SatelliteCapabilities from service definition to framework definition.
     * @param capabilities The SatelliteCapabilities from the satellite service.
     * @return The converted SatelliteCapabilities for the framework.
     */
    @Nullable public static SatelliteCapabilities fromSatelliteCapabilities(
            @Nullable android.telephony.satellite.stub.SatelliteCapabilities capabilities) {
        if (capabilities == null) return null;
        int[] radioTechnologies = capabilities.supportedRadioTechnologies == null
                ? new int[0] : capabilities.supportedRadioTechnologies;

        Map<Integer, AntennaPosition> antennaPositionMap = new HashMap<>();
        int[] antennaPositionKeys = capabilities.antennaPositionKeys;
        AntennaPosition[] antennaPositionValues = capabilities.antennaPositionValues;
        if (antennaPositionKeys != null && antennaPositionValues != null &&
                antennaPositionKeys.length == antennaPositionValues.length) {
            for(int i = 0; i < antennaPositionKeys.length; i++) {
                antennaPositionMap.put(antennaPositionKeys[i], antennaPositionValues[i]);
            }
        }

        return new SatelliteCapabilities(
                Arrays.stream(radioTechnologies)
                        .map(SatelliteServiceUtils::fromSatelliteRadioTechnology)
                        .boxed().collect(Collectors.toSet()),
                capabilities.isPointingRequired, capabilities.maxBytesPerOutgoingDatagram,
                antennaPositionMap);
    }

    /**
     * Convert PointingInfo from service definition to framework definition.
     * @param pointingInfo The PointingInfo from the satellite service.
     * @return The converted PointingInfo for the framework.
     */
    @Nullable public static PointingInfo fromPointingInfo(
            android.telephony.satellite.stub.PointingInfo pointingInfo) {
        if (pointingInfo == null) return null;
        return new PointingInfo(pointingInfo.satelliteAzimuth, pointingInfo.satelliteElevation);
    }

    /**
     * Convert SatelliteDatagram from service definition to framework definition.
     * @param datagram The SatelliteDatagram from the satellite service.
     * @return The converted SatelliteDatagram for the framework.
     */
    @Nullable public static SatelliteDatagram fromSatelliteDatagram(
            android.telephony.satellite.stub.SatelliteDatagram datagram) {
        if (datagram == null) return null;
        byte[] data = datagram.data == null ? new byte[0] : datagram.data;
        return new SatelliteDatagram(data);
    }

    /**
     * Convert SatelliteDatagram from framework definition to service definition.
     * @param datagram The SatelliteDatagram from the framework.
     * @return The converted SatelliteDatagram for the satellite service.
     */
    @Nullable public static android.telephony.satellite.stub.SatelliteDatagram toSatelliteDatagram(
            @Nullable SatelliteDatagram datagram) {
        android.telephony.satellite.stub.SatelliteDatagram converted =
                new android.telephony.satellite.stub.SatelliteDatagram();
        converted.data = datagram.getSatelliteDatagram();
        return converted;
    }

    /**
     * Get the {@link SatelliteManager.SatelliteResult} from the provided result.
     *
     * @param ar AsyncResult used to determine the error code.
     * @param caller The satellite request.
     *
     * @return The {@link SatelliteManager.SatelliteResult} error code from the request.
     */
    @SatelliteManager.SatelliteResult public static int getSatelliteError(@NonNull AsyncResult ar,
            @NonNull String caller) {
        int errorCode;
        if (ar.exception == null) {
            errorCode = SatelliteManager.SATELLITE_RESULT_SUCCESS;
        } else {
            errorCode = SatelliteManager.SATELLITE_RESULT_ERROR;
            if (ar.exception instanceof CommandException) {
                CommandException.Error error = ((CommandException) ar.exception).getCommandError();
                errorCode = RILUtils.convertToSatelliteError(error);
                loge(caller + " CommandException: " + ar.exception);
            } else if (ar.exception instanceof SatelliteManager.SatelliteException) {
                errorCode = ((SatelliteManager.SatelliteException) ar.exception).getErrorCode();
                loge(caller + " SatelliteException: " + ar.exception);
            } else {
                loge(caller + " unknown exception: " + ar.exception);
            }
        }
        logd(caller + " error: " + errorCode);
        return errorCode;
    }

    /**
     * Get valid subscription id for satellite communication.
     *
     * @param subId The subscription id.
     * @return input subId if the subscription is active else return default subscription id.
     */
    public static int getValidSatelliteSubId(int subId, @NonNull Context context) {
        final long identity = Binder.clearCallingIdentity();
        try {
            boolean isActive = SubscriptionManagerService.getInstance().isActiveSubId(subId,
                    context.getOpPackageName(), context.getAttributionTag());

            if (isActive) {
                return subId;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        logd("getValidSatelliteSubId: use DEFAULT_SUBSCRIPTION_ID for subId=" + subId);
        return SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    }

    /**
     * Expected format of the input dictionary bundle is:
     * <ul>
     *     <li>Key: PLMN string.</li>
     *     <li>Value: A string with format "service_1,service_2,..."</li>
     * </ul>
     * @return The map of supported services with key: PLMN, value: set of services supported by
     * the PLMN.
     */
    @NonNull
    @NetworkRegistrationInfo.ServiceType
    public static Map<String, Set<Integer>> parseSupportedSatelliteServices(
            PersistableBundle supportedServicesBundle) {
        Map<String, Set<Integer>> supportedServicesMap = new HashMap<>();
        if (supportedServicesBundle == null || supportedServicesBundle.isEmpty()) {
            return supportedServicesMap;
        }

        for (String plmn : supportedServicesBundle.keySet()) {
            Set<Integer> supportedServicesSet = new HashSet<>();
            for (int serviceType : supportedServicesBundle.getIntArray(plmn)) {
                if (isServiceTypeValid(serviceType)) {
                    supportedServicesSet.add(serviceType);
                } else {
                    loge("parseSupportedSatelliteServices: invalid service type=" + serviceType
                            + " for plmn=" + plmn);
                }
            }
            logd("parseSupportedSatelliteServices: plmn=" + plmn + ", supportedServicesSet="
                    + supportedServicesSet.stream().map(String::valueOf).collect(
                            joining(",")));
            supportedServicesMap.put(plmn, supportedServicesSet);
        }
        return supportedServicesMap;
    }

    /**
     * Merge two string lists into one such that the result list does not have any duplicate items.
     */
    @NonNull
    public static List<String> mergeStrLists(List<String> strList1, List<String> strList2) {
        Set<String> mergedStrSet = new HashSet<>();
        mergedStrSet.addAll(strList1);
        mergedStrSet.addAll(strList2);
        return mergedStrSet.stream().toList();
    }

    private static boolean isServiceTypeValid(int serviceType) {
        return (serviceType >= FIRST_SERVICE_TYPE && serviceType <= LAST_SERVICE_TYPE);
    }

    /**
     * Return phone associated with phoneId 0.
     *
     * @return phone associated with phoneId 0 or {@code null} if it doesn't exist.
     */
    public static @Nullable Phone getPhone() {
        return PhoneFactory.getPhone(0);
    }

    /**
     * Return phone associated with subscription ID.
     *
     * @return phone associated with {@code subId} or {@code null} if it doesn't exist.
     */
    public static @Nullable Phone getPhone(int subId) {
        return PhoneFactory.getPhone(subId);
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
