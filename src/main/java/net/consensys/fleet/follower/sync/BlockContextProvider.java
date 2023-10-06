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
package net.consensys.fleet.follower.sync;

import net.consensys.fleet.common.plugin.PluginServiceProvider;
import net.consensys.fleet.common.rpc.model.GetBlockParams;
import net.consensys.fleet.follower.rpc.client.FleetGetBlockClient;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;

public class BlockContextProvider {

  private final Cache<CompositeBlockKey, Optional<FleetBlockContext>> leaderBlock =
      CacheBuilder.newBuilder().maximumSize(20).expireAfterAccess(1, TimeUnit.MINUTES).build();

  private final Cache<CompositeBlockKey, Optional<FleetBlockContext>> localBlock =
      CacheBuilder.newBuilder().maximumSize(20).expireAfterAccess(1, TimeUnit.MINUTES).build();

  private final PluginServiceProvider pluginServiceProvider;
  private final FleetGetBlockClient getBlockClient;

  public BlockContextProvider(
      final PluginServiceProvider pluginServiceProvider, final FleetGetBlockClient getBlockClient) {
    this.pluginServiceProvider = pluginServiceProvider;
    this.getBlockClient = getBlockClient;
  }

  public Optional<FleetBlockContext> getLeaderBlockContextByNumber(final long blockNumber) {
    try {
      return leaderBlock.get(
          new CompositeBlockKey(blockNumber),
          () -> {
            GetBlockParams getBlockParams =
                getBlockClient.sendData(blockNumber).get(1, TimeUnit.SECONDS);
            return Optional.of(
                new FleetBlockContext(
                    getBlockParams.getBlockHeader(),
                    getBlockParams.getBlockBody(),
                    Optional.of(Bytes.fromHexString(getBlockParams.getTrieLogRlp()))));
          });
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public Optional<FleetBlockContext> getLocalBlockContextByNumber(final long number) {
    try {
      return localBlock.get(
          new CompositeBlockKey(number),
          () -> {
            final BlockchainService blockchainService =
                pluginServiceProvider.getService(BlockchainService.class);
            return blockchainService
                .getBlockByNumber(number)
                .map(
                    blockContext ->
                        new FleetBlockContext(
                            blockContext.getBlockHeader(),
                            blockContext.getBlockBody(),
                            Optional.empty()));
          });
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static class CompositeBlockKey {

    private long blockNumber;
    private Hash blockHash;

    public CompositeBlockKey(final long blockNumber, final Hash blockHash) {
      this.blockNumber = blockNumber;
      this.blockHash = blockHash;
    }

    public CompositeBlockKey(final long blockNumber) {
      this.blockNumber = blockNumber;
    }

    public CompositeBlockKey(final Hash blockHash) {
      this.blockHash = blockHash;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CompositeBlockKey that = (CompositeBlockKey) o;
      if (blockHash == null || that.blockHash == null) {
        return blockNumber == that.blockNumber;
      } else {
        return Objects.equals(blockHash, that.blockHash);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(blockNumber, blockHash);
    }
  }

  public static class FleetBlockContext implements BlockContext {
    private final BlockHeader blockHeader;
    private final BlockBody blockBody;
    private final Optional<Bytes> trieLogRlp;

    public FleetBlockContext(
        final BlockHeader blockHeader,
        final BlockBody blockBody,
        final Optional<Bytes> trieLogRlp) {
      this.blockHeader = blockHeader;
      this.blockBody = blockBody;
      this.trieLogRlp = trieLogRlp;
    }

    @Override
    public BlockHeader getBlockHeader() {
      return blockHeader;
    }

    @Override
    public BlockBody getBlockBody() {
      return blockBody;
    }

    public Optional<Bytes> trieLogRlp() {
      return trieLogRlp;
    }
  }
}
