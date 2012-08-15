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
package org.vertx.testing.junit;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.runner.Description;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.impl.VerticleManager;
import org.vertx.testing.junit.annotations.Module;
import org.vertx.testing.junit.annotations.Modules;
import org.vertx.testing.junit.annotations.Verticle;
import org.vertx.testing.junit.annotations.Verticles;


/**
 * @author swilliams
 *
 */
public class Deployer {

  private final File modDir;
  
  private final VerticleManager manager;

  Deployer(VerticleManager manager, File modDir) {
    this.manager = manager;
    this.modDir = modDir;
  }

  public void deploy(Description description) {
    final Modules amodules = description.getAnnotation(Modules.class);
    final Module amodule = description.getAnnotation(Module.class);
    final Verticles verticles = description.getAnnotation(Verticles.class);
    final Verticle verticle = description.getAnnotation(Verticle.class);

    deployModules(amodules);
    deployModule(amodule);
    deployVerticles(verticles);
    deployVerticle(verticle);    
  }


  private void deployVerticles(Verticles verticles) {

    if (verticles == null) {
      return;
    }

    final CountDownLatch latch = new CountDownLatch(verticles.value().length);
    for (Verticle v : verticles.value()) {
      JsonObject config = getJsonConfig(v.jsonConfig());

      URL[] urls = findVerticleURLs(v);
      manager.deployVerticle(v.worker(), v.main(), config, urls, v.instances(), modDir, new CountDownLatchDoneHandler<String>(latch));
    }

    await(latch);
  }

  private void deployVerticle(Verticle v) {
    if (v == null) {
      return;
    }

    final CountDownLatch latch = new CountDownLatch(1);
    JsonObject config = getJsonConfig(v.jsonConfig());
    URL[] urls = findVerticleURLs(v);
    manager.deployVerticle(v.worker(), v.main(), config, urls, v.instances(), modDir, new CountDownLatchDoneHandler<String>(latch));

    await(latch);
  }

  private void deployModules(Modules amodules) {
    if (amodules == null) {
      return;
    }

    final CountDownLatch latch = new CountDownLatch(amodules.value().length);

    for (Module m : amodules.value()) {
      JsonObject config = getJsonConfig(m.jsonConfig());
      manager.deployMod(m.name(), config, m.instances(), modDir, new CountDownLatchDoneHandler<String>(latch));
    }

    await(latch);
  }

  private void deployModule(Module m) {
    if (m == null) {
      return;
    }

    final CountDownLatch latch = new CountDownLatch(1);

    JsonObject config = getJsonConfig(m.jsonConfig());
    manager.deployMod(m.name(), config, m.instances(), modDir, new CountDownLatchDoneHandler<String>(latch));

    await(latch);
  }

  private URL[] findVerticleURLs(Verticle v) {
    Set<URL> urlSet = new HashSet<URL>();

    if (v.urls().length > 0) {
      for (String path : v.urls()) {
        try {

          URL url = new File(path).toURI().toURL();
          urlSet.add(url);

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    try {
      // contortions to get parent
      URL url = getClass().getClassLoader().getResource(v.main());
      url = Paths.get(url.toURI()).getParent().toUri().toURL();
      urlSet.add(url);

    } catch (Exception e) {
      e.printStackTrace();
    }

    URL[] urls = new URL[urlSet.size()];
    return urlSet.toArray(urls);
  }

  private JsonObject getJsonConfig(String jsonConfig) {
    JsonObject config;

    if (jsonConfig.startsWith("file:")) {
      String filename = jsonConfig.replaceFirst("file:", "");
      Path json = new File(filename).toPath();

      try {
        Charset utf8 = Charset.forName("UTF-8");
        byte[] bytes = Files.readAllBytes(json);
        config = new JsonObject(new String(bytes, utf8));

      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      config = new JsonObject(jsonConfig);
    }

    return config;
  }

  private void await(final CountDownLatch latch) {
    try {
      latch.await(30L, TimeUnit.SECONDS);

    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
