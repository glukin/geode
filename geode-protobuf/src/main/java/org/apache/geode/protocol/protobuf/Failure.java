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
package org.apache.geode.protocol.protobuf;

import java.util.function.Function;

public class Failure<SuccessType> implements Result<SuccessType> {
  private final ClientProtocol.ErrorResponse errorResponse;

  public Failure(ClientProtocol.ErrorResponse errorResponse) {
    this.errorResponse = errorResponse;
  }

  public static <T> Failure<T> of(ClientProtocol.ErrorResponse errorResponse) {
    return new Failure<>(errorResponse);
  }

  @Override
  public <T> T map(Function<SuccessType, T> successFunction,
      Function<ClientProtocol.ErrorResponse, T> errorFunction) {
    return errorFunction.apply(errorResponse);
  }

  @Override
  public SuccessType getMessage() {
    throw new RuntimeException("This is not a Success result");
  }

  @Override
  public ClientProtocol.ErrorResponse getErrorMessage() {
    return errorResponse;
  }
}
