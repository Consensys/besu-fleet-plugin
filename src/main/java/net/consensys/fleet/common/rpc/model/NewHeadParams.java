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
package net.consensys.fleet.common.rpc.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;

public class NewHeadParams {

  @JsonProperty("newHead")
  private BlockHeader head;

  @JsonProperty("headBody")
  private BlockBody blockBody;

  @JsonProperty("headReceipts")
  private List<TransactionReceipt> receipts;

  @JsonProperty("headTrieLogRlp")
  private String trieLogRlp;

  @JsonProperty("safeBlock")
  private Hash safeBlock;

  @JsonProperty("finalizedBlock")
  private Hash finalizedBlock;

  public NewHeadParams() {}

  public NewHeadParams(
      final BlockHeader head,
      final BlockBody blockBody,
      final List<TransactionReceipt> receipts,
      final String trieLogRlp,
      final Hash safeBlock,
      final Hash finalizedBlock) {
    this.head = head;
    this.blockBody = blockBody;
    this.receipts = receipts;
    this.trieLogRlp = trieLogRlp;
    this.safeBlock = safeBlock;
    this.finalizedBlock = finalizedBlock;
  }

  public BlockHeader getHead() {
    return head;
  }

  public BlockBody getBlockBody() {
    return blockBody;
  }

  public List<TransactionReceipt> getReceipts() {
    return receipts;
  }

  public String getTrieLogRlp() {
    return trieLogRlp;
  }

  public Hash getSafeBlock() {
    return safeBlock;
  }

  public Hash getFinalizedBlock() {
    return finalizedBlock;
  }
}
