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

package de.dytanic.cloudnet.driver.module;

import de.dytanic.cloudnet.common.log.LogManager;
import de.dytanic.cloudnet.common.log.Logger;
import de.dytanic.cloudnet.driver.module.util.ModuleDependencyUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * A default implementation of an {@link IModuleWrapper}.
 */
public class DefaultModuleWrapper implements IModuleWrapper {

  protected static final Logger LOGGER = LogManager.logger(DefaultModuleWrapper.class);
  // This looks strange in the first place but is the only way to go as java generics are a bit strange.
  // When using Comparator.comparingInt(...).reverse() the type is now a Comparator<Object> which leads to problems
  // extracting the key of the task entry... And yes, reversing is necessary as the module task with the highest order
  // should be called first but the natural ordering of java sorts the lowest number first.
  protected static final Comparator<IModuleTaskEntry> TASK_COMPARATOR = Comparator.comparing(
    entry -> entry.getTaskInfo().order(), Comparator.reverseOrder());

  private final URL source;
  private final URI sourceUri;
  private final IModule module;
  private final Path dataDirectory;
  private final IModuleProvider provider;
  private final URLClassLoader classLoader;
  private final Set<ModuleDependency> dependingModules;
  private final ModuleConfiguration moduleConfiguration;

  private final Lock moduleLifecycleUpdateLock = new ReentrantLock();
  private final Map<ModuleLifeCycle, List<IModuleTaskEntry>> tasks = new EnumMap<>(ModuleLifeCycle.class);
  private final AtomicReference<ModuleLifeCycle> lifeCycle = new AtomicReference<>(ModuleLifeCycle.CREATED);

  /**
   * Creates a new instance of a default module wrapper.
   *
   * @param source              the module file from which this module was loaded initially.
   * @param module              the instance of the module main class constructed by the provider.
   * @param dataDirectory       the data directory of this module relative to the module provider directory.
   * @param provider            the provider which loaded this module.
   * @param classLoader         the class loader which was used to load the main class from the file.
   * @param dependingModules    the modules this module depends on and which need to get loaded first.
   * @param moduleConfiguration the parsed module configuration located in the module file.
   * @throws URISyntaxException if the given module source is not formatted strictly according to RFC2396.
   */
  public DefaultModuleWrapper(
    URL source,
    IModule module,
    Path dataDirectory,
    IModuleProvider provider,
    URLClassLoader classLoader,
    Set<ModuleDependency> dependingModules,
    ModuleConfiguration moduleConfiguration
  ) throws URISyntaxException {
    this.source = source;
    this.module = module;
    this.dataDirectory = dataDirectory;
    this.provider = provider;
    this.classLoader = classLoader;
    this.dependingModules = dependingModules;
    this.moduleConfiguration = moduleConfiguration;
    // initialize the uri of the module now as it's always required in order for the default provider to work
    this.sourceUri = source.toURI();
    // resolve all tasks the module must execute now as we need them later anyways
    this.tasks.putAll(this.resolveModuleTasks(module));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull Map<ModuleLifeCycle, List<IModuleTaskEntry>> getModuleTasks() {
    return Collections.unmodifiableMap(this.tasks);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull @Unmodifiable Set<ModuleDependency> getDependingModules() {
    return Collections.unmodifiableSet(this.dependingModules);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModule getModule() {
    return this.module;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ModuleLifeCycle getModuleLifeCycle() {
    return this.lifeCycle.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleProvider getModuleProvider() {
    return this.provider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ModuleConfiguration getModuleConfiguration() {
    return this.moduleConfiguration;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ClassLoader getClassLoader() {
    return this.classLoader;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleWrapper loadModule() {
    this.pushLifecycleChange(ModuleLifeCycle.LOADED, true);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleWrapper startModule() {
    if (this.getModuleLifeCycle().canChangeTo(ModuleLifeCycle.STARTED)
      && this.provider.notifyPreModuleLifecycleChange(this, ModuleLifeCycle.STARTED)) {
      // Resolve all dependencies of this module to start them before this module
      for (var wrapper : ModuleDependencyUtils.collectDependencies(this, this.provider)) {
        wrapper.startModule();
      }
      // now we can start this module
      this.pushLifecycleChange(ModuleLifeCycle.STARTED, false);
      // and we now need to notify the provider here
      this.provider.notifyPostModuleLifecycleChange(this, ModuleLifeCycle.STARTED);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleWrapper reloadModule() {
    if (this.getModuleLifeCycle().canChangeTo(ModuleLifeCycle.RELOADING)
      && this.provider.notifyPreModuleLifecycleChange(this, ModuleLifeCycle.RELOADING)) {
      // Resolve all dependencies of this module to reload them before this module
      for (var wrapper : ModuleDependencyUtils.collectDependencies(this, this.provider)) {
        wrapper.reloadModule();
      }
      //now we can reload this module
      this.pushLifecycleChange(ModuleLifeCycle.RELOADING, false);
      // and we now need to notify the provider here
      this.provider.notifyPostModuleLifecycleChange(this, ModuleLifeCycle.RELOADING);
      //push the module back to started
      this.lifeCycle.set(ModuleLifeCycle.STARTED);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleWrapper stopModule() {
    this.pushLifecycleChange(ModuleLifeCycle.STOPPED, true);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull IModuleWrapper unloadModule() {
    if (this.getModuleLifeCycle().canChangeTo(ModuleLifeCycle.UNLOADED)) {
      this.pushLifecycleChange(ModuleLifeCycle.UNLOADED, true);
      // remove all known module tasks & dependencies
      this.tasks.clear();
      this.dependingModules.clear();
      // close the class loader
      try {
        this.classLoader.close();
      } catch (IOException exception) {
        LOGGER.severe(
          String.format("Exception closing class loader of module %s", this.moduleConfiguration.getName()),
          exception
        );
      }
      // set the state to unusable
      this.lifeCycle.set(ModuleLifeCycle.UNUSEABLE);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull Path getDataDirectory() {
    return this.dataDirectory;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull URL getUrl() {
    return this.source;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull URI getUri() {
    return this.sourceUri;
  }

  /**
   * Resolves all module tasks in the main class of a module. Declared methods are working as well. The returned map
   * should only contain the tasks sorted as there will be no later step to sort them. Not all module lifecycle states
   * must be in the returned map, if a lifecycle is missing it will be assumed that there is no tasks for that
   * lifecycle.
   *
   * @param module the module instance (better known as the module main class) to resolve the tasks of.
   * @return all sorted resolved tasks mapped to the lifecycle they will be called in.
   */
  protected @NotNull Map<ModuleLifeCycle, List<IModuleTaskEntry>> resolveModuleTasks(@NotNull IModule module) {
    Map<ModuleLifeCycle, List<IModuleTaskEntry>> result = new EnumMap<>(ModuleLifeCycle.class);
    // check all declared methods to get all methods of this and super classes
    for (var method : module.getClass().getDeclaredMethods()) {
      // check if this method is a method we need to register
      var moduleTask = method.getAnnotation(ModuleTask.class);
      if (moduleTask != null && method.getParameterCount() == 0) {
        try {
          // ensure that we can access the method before we register it, this will never fail on public methods as
          // long as there is no module denying the access
          method.setAccessible(true);
        } catch (RuntimeException exception) {
          // we want to catch the InaccessibleObjectException (which is a RuntimeException) but not available on Java 8
          // If this happens we only print a warning and continue our search (this might cause further module issues
          // as one task will not get fired at all but this is not our mistake, so we can safely ignore it)
          LOGGER.warning(String.format("Unable to module task declared by method %s@%s() accessible, ignoring.",
            method.getDeclaringClass().getCanonicalName(), method.getName()));
          continue;
        }
        // now try to register the method
        try {
          var entries = result.computeIfAbsent(moduleTask.event(), $ -> new ArrayList<>());
          entries.add(new DefaultModuleTaskEntry(this, moduleTask, method));
          // re-sort the list now as we don't want to re-iterate later
          entries.sort(TASK_COMPARATOR);
        } catch (IllegalAccessException exception) {
          // this should not happen as we had successfully overridden the java lang access flag earlier
          LOGGER.severe("Unable to access module task entry to unreflect method", exception);
        }
      }
    }
    return result;
  }

  /**
   * Fires all handlers registered for the specified {@code lifeCycle}.
   *
   * @param lifeCycle      the lifecycle to fire the tasks of.
   * @param notifyProvider if the module provider should be notified about the change or not.
   */
  protected void pushLifecycleChange(@NotNull ModuleLifeCycle lifeCycle, boolean notifyProvider) {
    var tasks = this.tasks.get(lifeCycle);
    if (this.getModuleLifeCycle().canChangeTo(lifeCycle)) {
      this.moduleLifecycleUpdateLock.lock();
      try {
        // notify the provider for changes which are required based on the lifecycle and other stuff (like to invoke
        // of the associated methods in the module provider handler)
        if (!notifyProvider || this.getModuleProvider().notifyPreModuleLifecycleChange(this, lifeCycle)) {
          // The tasks are always in the logical order in the backing map, so there is no need to sort here
          if (tasks != null && !tasks.isEmpty()) {
            for (var task : tasks) {
              if (this.fireModuleTaskEntry(task)) {
                // we couldn't complete firing all tasks as one failed, so we break here and warn the user about that.
                LOGGER.warning(String.format(
                  "Stopping lifecycle update to %s for %s because the task %s failed. See console log for more details.",
                  lifeCycle, this.moduleConfiguration.getName(), task.getFullMethodSignature()
                ));
                break;
              }
            }
          }
          // actually set the current life cycle of this module
          this.lifeCycle.set(lifeCycle);
          // notify after the change again if we have to
          if (notifyProvider) {
            this.getModuleProvider().notifyPostModuleLifecycleChange(this, lifeCycle);
          }
        }
      } finally {
        this.moduleLifecycleUpdateLock.unlock();
      }
    }
  }

  /**
   * Fires a specific module task entry.
   *
   * @param entry the entry to fire.
   * @return {@code true} if the entry couldn't be fired successfully, {@code false} otherwise.
   */
  protected boolean fireModuleTaskEntry(@NotNull IModuleTaskEntry entry) {
    try {
      entry.fire();
      return false;
    } catch (Throwable exception) {
      LOGGER.severe("Exception firing module task entry " + entry, exception);
      return true;
    }
  }
}
