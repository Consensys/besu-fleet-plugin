/*
 * Copyright ConsenSys 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.fleet.common.rpc.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.hyperledger.besu.datatypes.Hash;

public class HashJsonSerializer extends JsonSerializer<Object> implements ContextualSerializer {

  @Override
  public void serialize(
      final Object value, final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeString(((Hash) value).toHexString());
  }

  @Override
  public JsonSerializer<?> createContextual(
      final SerializerProvider prov, final BeanProperty property) {
    return new HashJsonSerializer();
  }
}
