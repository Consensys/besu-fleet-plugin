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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;

public class BlockReceiptJsonDeserializer extends JsonDeserializer<TransactionReceipt>
    implements ContextualDeserializer {
  private final RlpConverterService rlpConverterService;

  public BlockReceiptJsonDeserializer(final RlpConverterService rlpConverterService) {
    this.rlpConverterService = rlpConverterService;
  }

  @Override
  public TransactionReceipt deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return rlpConverterService.buildReceiptFromRlp(Bytes.fromHexString(p.getValueAsString()));
  }

  @Override
  public JsonDeserializer<?> createContextual(
      final DeserializationContext ctxt, final BeanProperty property) {
    return new BlockReceiptJsonDeserializer(rlpConverterService);
  }
}
