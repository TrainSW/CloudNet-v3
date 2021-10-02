/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.ext.bridge.bukkit.event;

import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

/**
 * Use {@link de.dytanic.cloudnet.driver.event.events.service.CloudServiceStartEvent} instead with the {@link
 * de.dytanic.cloudnet.driver.event.EventListener} annotation and register it using {@link
 * de.dytanic.cloudnet.driver.event.IEventManager#registerListener(Object)}
 */
@Deprecated
@ScheduledForRemoval(inVersion = "3.6")
public final class BukkitCloudServiceStartEvent extends BukkitCloudNetEvent {

  private static final HandlerList handlerList = new HandlerList();

  private final ServiceInfoSnapshot serviceInfoSnapshot;

  public BukkitCloudServiceStartEvent(ServiceInfoSnapshot serviceInfoSnapshot) {
    this.serviceInfoSnapshot = serviceInfoSnapshot;
  }

  public static HandlerList getHandlerList() {
    return BukkitCloudServiceStartEvent.handlerList;
  }

  @NotNull
  @Override
  public HandlerList getHandlers() {
    return handlerList;
  }

  public ServiceInfoSnapshot getServiceInfoSnapshot() {
    return this.serviceInfoSnapshot;
  }
}
