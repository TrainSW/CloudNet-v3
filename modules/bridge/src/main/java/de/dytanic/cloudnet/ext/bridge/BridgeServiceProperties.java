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

package de.dytanic.cloudnet.ext.bridge;

import static de.dytanic.cloudnet.driver.service.property.DefaultJsonServiceProperty.createFromClass;
import static de.dytanic.cloudnet.driver.service.property.DefaultJsonServiceProperty.createFromType;

import com.google.gson.reflect.TypeToken;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.driver.service.ServiceLifeCycle;
import de.dytanic.cloudnet.driver.service.property.DefaultFunctionalServiceProperty;
import de.dytanic.cloudnet.driver.service.property.DefaultModifiableServiceProperty;
import de.dytanic.cloudnet.driver.service.property.ServiceProperty;
import de.dytanic.cloudnet.ext.bridge.player.ServicePlayer;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Properties in ServiceInfos by the bridge module.
 */
public final class BridgeServiceProperties {

  /**
   * Property to get the online count of a service.
   */
  public static final ServiceProperty<Integer> ONLINE_COUNT = createFromClass("Online-Count", Integer.class)
    .forbidModification();
  /**
   * Property to get the max players of a service.
   */
  public static final ServiceProperty<Integer> MAX_PLAYERS = createFromClass("Max-Players", Integer.class)
    .forbidModification();
  /**
   * Property to get the Bukkit/Bungee/Nukkit/Velocity version of a service.
   */
  public static final ServiceProperty<String> VERSION = createFromClass("Version", String.class).forbidModification();
  /**
   * Property to get the Motd of a service.
   */
  public static final ServiceProperty<String> MOTD = createFromClass("Motd", String.class).forbidModification();
  /**
   * Property to get the Extra of a service.
   */
  public static final ServiceProperty<String> EXTRA = createFromClass("Extra", String.class).forbidModification();
  /**
   * Property to get the State of a service.
   */
  public static final ServiceProperty<String> STATE = createFromClass("State", String.class).forbidModification();
  /**
   * Property to check whether a service is online or not.
   */
  public static final ServiceProperty<Boolean> IS_ONLINE = createFromClass("Online", Boolean.class)
    .forbidModification();
  /**
   * Property to check whether a service is in game or not.
   */
  public static final ServiceProperty<Boolean> IS_IN_GAME = DefaultFunctionalServiceProperty.<Boolean>create()
    .get(BridgeServiceProperties::isInGameService);
  /**
   * Property to check whether a service is starting or not.
   */
  public static final ServiceProperty<Boolean> IS_STARTING = DefaultFunctionalServiceProperty.<Boolean>create()
    .get(BridgeServiceProperties::isStartingService);
  /**
   * Property to check whether a service is empty (no players) or not.
   */
  public static final ServiceProperty<Boolean> IS_EMPTY = DefaultFunctionalServiceProperty.<Boolean>create()
    .get(BridgeServiceProperties::isEmptyService);
  /**
   * Property to check whether a service is full (online count &gt;= max players) or not.
   */
  public static final ServiceProperty<Boolean> IS_FULL = DefaultFunctionalServiceProperty.<Boolean>create()
    .get(BridgeServiceProperties::isFullService);
  /**
   * Property to get all online players on a service.
   */
  public static final ServiceProperty<Collection<ServicePlayer>> PLAYERS = DefaultModifiableServiceProperty
    .<Collection<JsonDocument>, Collection<ServicePlayer>>wrap(
      createFromType("Players", new TypeToken<Collection<JsonDocument>>() {
      }.getType()))
    .modifyGet(($, documents) -> documents.stream().map(ServicePlayer::new).collect(Collectors.toList()));

  private BridgeServiceProperties() {
    throw new UnsupportedOperationException();
  }

  private static boolean isEmptyService(@NotNull ServiceInfoSnapshot service) {
    return service.isConnected()
      && service.getProperty(IS_ONLINE).orElse(false)
      && service.getProperty(ONLINE_COUNT).isPresent()
      && service.getProperty(ONLINE_COUNT).orElse(0) == 0;
  }

  private static boolean isFullService(@NotNull ServiceInfoSnapshot service) {
    return service.isConnected()
      && service.getProperty(IS_ONLINE).orElse(false)
      && service.getProperty(ONLINE_COUNT).isPresent()
      && service.getProperty(MAX_PLAYERS).isPresent()
      && service.getProperty(ONLINE_COUNT).orElse(0) >= service.getProperty(MAX_PLAYERS).orElse(0);
  }

  private static boolean isStartingService(@NotNull ServiceInfoSnapshot service) {
    return service.getLifeCycle() == ServiceLifeCycle.RUNNING && !service.getProperty(IS_ONLINE).orElse(false);
  }

  private static boolean isInGameService(@NotNull ServiceInfoSnapshot service) {
    return service.getLifeCycle() == ServiceLifeCycle.RUNNING && service.isConnected()
      && service.getProperty(IS_ONLINE).orElse(false)
      && (service.getProperty(MOTD).map(BridgeServiceProperties::matchesInGameString).orElse(false) ||
      service.getProperty(EXTRA).map(BridgeServiceProperties::matchesInGameString).orElse(false) ||
      service.getProperty(STATE).map(BridgeServiceProperties::matchesInGameString).orElse(false));
  }

  private static boolean matchesInGameString(@NotNull String value) {
    value = value.toLowerCase();
    return value.contains("ingame") || value.contains("running") || value.contains("playing");
  }
}
