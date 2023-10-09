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
package net.consensys.fleet.follower.event;

import net.consensys.fleet.follower.sync.FleetModeSynchronizer;

import java.util.function.Supplier;

import org.hyperledger.besu.plugin.services.BesuEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitialSyncCompletionObserver implements BesuEvents.InitialSyncCompletionListener {
  private static final Logger LOG = LoggerFactory.getLogger(InitialSyncCompletionObserver.class);

  private final Supplier<FleetModeSynchronizer> fleetModeSynchronizerSupplier;

  public InitialSyncCompletionObserver(
      final Supplier<FleetModeSynchronizer> fleetModeSynchronizerSupplier) {
    this.fleetModeSynchronizerSupplier = fleetModeSynchronizerSupplier;
  }

  @Override
  public void onInitialSyncCompleted() {
    if (fleetModeSynchronizerSupplier.get() != null) {
      fleetModeSynchronizerSupplier.get().disableInitialSync();
    } else {
      LOG.debug("Cannot disable initial sync because fleet mode synchronizer is not ready");
    }
  }

  @Override
  public void onInitialSyncRestart() {}
}
