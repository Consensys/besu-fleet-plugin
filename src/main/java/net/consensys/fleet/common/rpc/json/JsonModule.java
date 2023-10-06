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

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;

public class JsonModule extends SimpleModule {

  public JsonModule(final RlpConverterService rlpConverterService) {
    super();
    addSerializer(BlockHeader.class, new BlockHeaderJsonSerializer(rlpConverterService));
    addDeserializer(BlockHeader.class, new BlockHeaderJsonDeserializer(rlpConverterService));
    addSerializer(BlockBody.class, new BlockBodyJsonSerializer(rlpConverterService));
    addDeserializer(BlockBody.class, new BlockBodyJsonDeserializer(rlpConverterService));
    addSerializer(TransactionReceipt.class, new BlockReceiptJsonSerializer(rlpConverterService));
  }
}
