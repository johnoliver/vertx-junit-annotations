/*
 * Copyright 2012 the original author or authors.
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
package org.vertx.java.test.junit;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.VerticleManager;
import org.vertx.java.test.VertxConfiguration;
import org.vertx.java.test.utils.CountDownLatchDoneHandler;
import org.vertx.java.test.utils.DeploymentRegistry;
import org.vertx.java.test.utils.DeploymentUtils;
import org.vertx.java.test.utils.InjectionUtils;

/**
 * @author swilliams
 *
 */
public class VertxJUnit4ClassRunner extends JUnit4ClassRunnerAdapter {

  private VertxInternal vertx;

  private VerticleManager manager;

  private VertxConfiguration configuration;

  private File modDir;

  private Map<Annotation, String> classDeployments;

  private Map<Annotation, String> methodDeployments;

  private Verticle verticle;

  public VertxJUnit4ClassRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void beforeAll() {

    VertxBuilder builder = new VertxBuilder();

    String vertxMods = System.getProperty("vertx.mods", "build/tmp/mods-test");
    String vertxRepo = System.getProperty("vertx.test.repo");
    this.configuration = getDescription().getAnnotation(VertxConfiguration.class);
    if (configuration != null) {
      if (!"".equalsIgnoreCase(configuration.hostname())) {
        builder.setAddress(configuration.hostname());
      }
      if (configuration.port() > 0) {
        builder.setPort(configuration.port());
      }
      if (!"".equalsIgnoreCase(configuration.modsDir())) {
        vertxMods = configuration.modsDir();
        System.setProperty("vertx.mods", vertxMods);
      }
      if (!"".equalsIgnoreCase(configuration.repo())) {
          vertxRepo = configuration.repo();
        }
    }

    this.modDir = new File(vertxMods);
    if (!modDir.exists()) {
      modDir.mkdirs();
    }

    this.vertx = builder.build();
    this.manager = new VerticleManager(vertx, vertxRepo);
  }

  @Override
  protected void beforeCreateTest() {
    //
  }

  @Override
  protected void afterCreateTest(Object target) {

    if (target instanceof Verticle) {
      this.verticle = (Verticle) target;
      verticle.setVertx(vertx);
      verticle.setContainer(new Container(manager));
      try {
        System.out.println("Starting test verticle!");
        verticle.start();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  protected void injectResources(Object target) {
    if ((configuration != null && configuration.injectResources()) || configuration == null) {
      InjectionUtils.inject(vertx, target);
      InjectionUtils.inject(manager, target);
    }
  }

  @Override
  protected void injectMethodResources(FrameworkMethod method, Object target) {
//    injectResources(target); // TODO probable double setting... test & remove
  }

  @Override
  protected void beforeClass() {
    long timeout = Long.getLong("vertx.test.timeout", 15000L);
    this.classDeployments = JUnitDeploymentUtils.deploy(manager, modDir, getDescription(), timeout);
  }

  @Override
  protected void beforeTest(Description description, Object target) {
    long timeout = Long.getLong("vertx.test.timeout", 15000L);
    this.methodDeployments = JUnitDeploymentUtils.deploy(manager, modDir, description, timeout);
    DeploymentRegistry.register(methodDeployments);
  }

  @Override
  protected void afterTest(Description description, Object target) {
    if (methodDeployments.size() > 0) {
      DeploymentUtils.undeploy(manager, methodDeployments);
    }

    // avoid leaks
    DeploymentRegistry.clear();
  }

  @Override
  protected void beforeAfterClass() {

    if (this.verticle != null) {
      try {
        verticle.stop();
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void afterClass() {
    if (classDeployments.size() > 0) {
      DeploymentUtils.undeploy(manager, classDeployments);
    }
  }

  @Override
  protected void afterAll() {
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatchDoneHandler<Void> h = new CountDownLatchDoneHandler<>(latch);
    manager.undeployAll(h);
    long timeout = 5L;
    if (configuration != null) {
      timeout = configuration.shutdownTimeoutSeconds();
    }
    try {
      boolean awaited = latch.await(timeout, TimeUnit.SECONDS);
      if (!awaited) {
        System.out.println("Waited for " + timeout + " but not all undeployments may have completed");
      }
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

}
