/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache.tier.sockets.command;

import java.io.IOException;

import org.apache.geode.cache.DynamicRegionFactory;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.operations.RegisterInterestOperationContext;
import org.apache.geode.i18n.StringId;
import org.apache.geode.internal.Version;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.tier.CachedRegionHelper;
import org.apache.geode.internal.cache.tier.Command;
import org.apache.geode.internal.cache.tier.InterestType;
import org.apache.geode.internal.cache.tier.MessageType;
import org.apache.geode.internal.cache.tier.sockets.BaseCommand;
import org.apache.geode.internal.cache.tier.sockets.CacheClientProxy;
import org.apache.geode.internal.cache.tier.sockets.ChunkedMessage;
import org.apache.geode.internal.cache.tier.sockets.Message;
import org.apache.geode.internal.cache.tier.sockets.Part;
import org.apache.geode.internal.cache.tier.sockets.ServerConnection;
import org.apache.geode.internal.cache.vmotion.VMotionObserver;
import org.apache.geode.internal.cache.vmotion.VMotionObserverHolder;
import org.apache.geode.internal.i18n.LocalizedStrings;
import org.apache.geode.internal.logging.log4j.LocalizedMessage;
import org.apache.geode.internal.security.AuthorizeRequest;
import org.apache.geode.internal.security.SecurityService;

/**
 * @since GemFire 6.1
 */
public class RegisterInterest61 extends BaseCommand {

  private final static RegisterInterest61 singleton = new RegisterInterest61();

  /**
   * A debug flag used for testing vMotion during CQ registration
   */
  public static boolean VMOTION_DURING_REGISTER_INTEREST_FLAG = false;

  public static Command getCommand() {
    return singleton;
  }

  RegisterInterest61() {}

  @Override
  public void cmdExecute(final Message clientMessage, final ServerConnection serverConnection,
      final SecurityService securityService, long start) throws IOException, InterruptedException {
    Part regionNamePart = null, keyPart = null;
    String regionName = null;
    Object key = null;
    CachedRegionHelper crHelper = serverConnection.getCachedRegionHelper();
    serverConnection.setAsTrue(REQUIRES_RESPONSE);
    serverConnection.setAsTrue(REQUIRES_CHUNKED_RESPONSE);

    // bserverStats.incLong(readDestroyRequestTimeId,
    // DistributionStats.getStatTime() - start);
    // bserverStats.incInt(destroyRequestsId, 1);
    // start = DistributionStats.getStatTime();
    // Retrieve the data from the message parts
    regionNamePart = clientMessage.getPart(0);
    regionName = regionNamePart.getString();
    InterestResultPolicy policy = null;
    // Retrieve the interest type
    int interestType = clientMessage.getPart(1).getInt();

    // Retrieve the InterestResultPolicy
    try {
      policy = (InterestResultPolicy) clientMessage.getPart(2).getObject();
    } catch (Exception e) {
      writeChunkedException(clientMessage, e, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }
    boolean isDurable = false;
    try {
      Part durablePart = clientMessage.getPart(3);
      byte[] durablePartBytes = (byte[]) durablePart.getObject();
      isDurable = durablePartBytes[0] == 0x01;
    } catch (Exception e) {
      writeChunkedException(clientMessage, e, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }
    // region data policy
    byte[] regionDataPolicyPartBytes;
    boolean serializeValues = false;
    try {
      Part regionDataPolicyPart = clientMessage.getPart(clientMessage.getNumberOfParts() - 1);
      regionDataPolicyPartBytes = (byte[]) regionDataPolicyPart.getObject();
      if (serverConnection.getClientVersion().compareTo(Version.GFE_80) >= 0) {
        // The second byte here is serializeValues
        serializeValues = regionDataPolicyPartBytes[1] == (byte) 0x01;
      }
    } catch (Exception e) {
      writeChunkedException(clientMessage, e, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }
    // Retrieve the key
    keyPart = clientMessage.getPart(4);
    regionName = regionNamePart.getString();
    try {
      key = keyPart.getStringOrObject();
    } catch (Exception e) {
      writeChunkedException(clientMessage, e, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }

    boolean sendUpdatesAsInvalidates = false;

    // VJR: Check for a sixth part for client version 6.0.3 onwards for the
    // time being until refactoring into a new command version.
    if (clientMessage.getNumberOfParts() > 5) {
      try {
        Part notifyPart = clientMessage.getPart(5);
        byte[] notifyPartBytes = (byte[]) notifyPart.getObject();
        sendUpdatesAsInvalidates = notifyPartBytes[0] == 0x01;
      } catch (Exception e) {
        writeChunkedException(clientMessage, e, serverConnection);
        serverConnection.setAsTrue(RESPONDED);
        return;
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "{}: Received register interest 61 request ({} bytes) from {} for region {} key {}",
          serverConnection.getName(), clientMessage.getPayloadLength(),
          serverConnection.getSocketString(), regionName, key);
    }

    // test hook to trigger vMotion during register Interest

    if (VMOTION_DURING_REGISTER_INTEREST_FLAG) {
      VMotionObserver vmo = VMotionObserverHolder.getInstance();
      vmo.vMotionBeforeRegisterInterest();
    }

    // Process the register interest request
    if (key == null || regionName == null) {
      StringId message = null;
      if (key == null) {
        message =
            LocalizedStrings.RegisterInterest_THE_INPUT_KEY_FOR_THE_REGISTER_INTEREST_REQUEST_IS_NULL;
      }
      if (regionName == null) {
        message =
            LocalizedStrings.RegisterInterest_THE_INPUT_REGION_NAME_FOR_THE_REGISTER_INTEREST_REQUEST_IS_NULL;
      }
      logger.warn("{}: {}", serverConnection.getName(), message.toLocalizedString());
      writeChunkedErrorResponse(clientMessage, MessageType.REGISTER_INTEREST_DATA_ERROR,
          message.toLocalizedString(), serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }

    // input key not null
    LocalRegion region = (LocalRegion) serverConnection.getCache().getRegion(regionName);
    if (region == null) {
      logger.info(LocalizedMessage.create(
          LocalizedStrings.RegisterInterest_0_REGION_NAMED_1_WAS_NOT_FOUND_DURING_REGISTER_INTEREST_REQUEST,
          new Object[] {serverConnection.getName(), regionName}));
      // writeChunkedErrorResponse(msg,
      // MessageType.REGISTER_INTEREST_DATA_ERROR, message);
      // responded = true;
    }
    // Register interest
    try {

      if (interestType == InterestType.REGULAR_EXPRESSION) {
        securityService.authorizeRegionRead(regionName);
      } else {
        securityService.authorizeRegionRead(regionName, key.toString());
      }

      AuthorizeRequest authzRequest = serverConnection.getAuthzRequest();
      if (authzRequest != null) {
        if (!DynamicRegionFactory.regionIsDynamicRegionList(regionName)) {
          RegisterInterestOperationContext registerContext =
              authzRequest.registerInterestAuthorize(regionName, key, interestType, policy);
          key = registerContext.getKey();
        }
      }
      serverConnection.getAcceptor().getCacheClientNotifier().registerClientInterest(regionName,
          key, serverConnection.getProxyID(), interestType, isDurable, sendUpdatesAsInvalidates,
          true, regionDataPolicyPartBytes[0], true);
    } catch (Exception e) {
      // If an interrupted exception is thrown , rethrow it
      checkForInterrupt(serverConnection, e);
      // Otherwise, write an exception message and continue
      writeChunkedException(clientMessage, e, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }

    // System.out.println("Received register interest for " + regionName);

    // Update the statistics and write the reply
    // bserverStats.incLong(processDestroyTimeId,
    // DistributionStats.getStatTime() - start);
    // start = DistributionStats.getStatTime();

    CacheClientProxy ccp = serverConnection.getAcceptor().getCacheClientNotifier()
        .getClientProxy(serverConnection.getProxyID());
    if (ccp == null) {
      // fix for 37593
      IOException ioex = new IOException(
          LocalizedStrings.RegisterInterest_CACHECLIENTPROXY_FOR_THIS_CLIENT_IS_NO_LONGER_ON_THE_SERVER_SO_REGISTERINTEREST_OPERATION_IS_UNSUCCESSFUL
              .toLocalizedString());
      writeChunkedException(clientMessage, ioex, serverConnection);
      serverConnection.setAsTrue(RESPONDED);
      return;
    }
    boolean isPrimary = ccp.isPrimary();
    ChunkedMessage chunkedResponseMsg = serverConnection.getRegisterInterestResponseMessage();
    if (!isPrimary) {
      chunkedResponseMsg.setMessageType(MessageType.RESPONSE_FROM_SECONDARY);
      chunkedResponseMsg.setTransactionId(clientMessage.getTransactionId());
      chunkedResponseMsg.sendHeader();
      chunkedResponseMsg.setLastChunk(true);

      if (logger.isDebugEnabled()) {
        logger.debug(
            "{}: Sending register interest response chunk from secondary for region: {} for key: {} chunk=<{}>",
            serverConnection.getName(), regionName, key, chunkedResponseMsg);
      }
      chunkedResponseMsg.sendChunk(serverConnection);
    } // !isPrimary
    else { // isPrimary

      // Send header which describes how many chunks will follow
      chunkedResponseMsg.setMessageType(MessageType.RESPONSE_FROM_PRIMARY);
      chunkedResponseMsg.setTransactionId(clientMessage.getTransactionId());
      chunkedResponseMsg.sendHeader();

      // Send chunk response
      try {
        fillAndSendRegisterInterestResponseChunks(region, key, interestType, serializeValues,
            policy, serverConnection);
        serverConnection.setAsTrue(RESPONDED);
      } catch (Exception e) {
        writeChunkedException(clientMessage, e, serverConnection, chunkedResponseMsg);
        serverConnection.setAsTrue(RESPONDED);
        return;
      }

      if (logger.isDebugEnabled()) {
        // logger.debug(getName() + ": Sent chunk (1 of 1) of register interest
        // response (" + chunkedResponseMsg.getBufferLength() + " bytes) for
        // region " + regionName + " key " + key);
        logger.debug("{}: Sent register interest response for region {} key {}",
            serverConnection.getName(), regionName, key);
      }
      // bserverStats.incLong(writeDestroyResponseTimeId,
      // DistributionStats.getStatTime() - start);
      // bserverStats.incInt(destroyResponsesId, 1);
    } // isPrimary

  }

}
