/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI;

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APIGetRecordingSummary extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APIGetRecordingSummary.class);

    String cam = "";

    public APIGetRecordingSummary() {
        super(MQTT_GETRECORDINGSUMMARY_SUFFIX);
    }

    public ResultStruct ParseFromBits(String[] bits, @Nullable String payload) {
        ResultStruct rc = new ResultStruct();
        if (bits.length == 4) {
            this.cam = bits[2];

            // Why do we check the label again, since we already did it in the
            // cam handler? Well, some muppet may have poked us with this message
            // manually....

            rc = this.Validate();
        } else {
            rc.message = "internal communication error";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix) {

        ResultStruct rc = new ResultStruct();
        String apiCall = "/api/" + cam + "/recordings/summary";
        logger.info("posting: POST '{}'", apiCall);
        rc = httpHelper.runGet(apiCall);

        String camTopicPrefix = topicPrefix + "/" + cam + "/" + MQTT_CAMACTIONRESULT;
        if (rc.rc) {
            logger.info("event trigger response returned {}", rc.message);
            connection.publish(camTopicPrefix, rc.raw, 1, false);
        } else {
            String errFormat = String.format("{\"success\":false,\"message\":\"%s\"}", rc.message);
            connection.publish(camTopicPrefix, errFormat.getBytes(), 1, false);
        }
        return rc;
    }

    public ResultStruct Validate() {
        // nothing to validate on the input side.
        return new ResultStruct(true, "ok");
    }

    protected String BuildTopicSuffix() {
        return eventID;
    }
}