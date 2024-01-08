package com.penyo.tsington;

import com.penyo.tsington.v0.ConfigClassBasedTsingtonDataSource;
import com.penyo.tsington.v0.ClassPathYmlBasedTsingtonDataSource;
import com.penyo.tsington.v0.TsingtonDataSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FlexibleMechanismTest {
  @Test
  public void testConnectionAvailable() {
    try (TsingtonDataSource tds = new ClassPathYmlBasedTsingtonDataSource("tsington.config.yml")) {
      assertNotNull(tds.getConnection());
    }
  }

  public void expand(TsingtonDataSource tds) {
    // the followings will cause 2 rounds of invoking expand()
    for (int i = 0; i < 100; i++) {
      Thread t = new Thread(tds::getConnection);
      try {
        Thread.sleep(60);
      } catch (InterruptedException ignored) {
      }
      t.start();
    }

    // give monitor some time to react
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {
    }
  }

  @Test
  public void testExpandWithClassPathYml() {
    try (TsingtonDataSource tds = new ClassPathYmlBasedTsingtonDataSource("tsington.config.yml")) {
      expand(tds);
      assertEquals(tds.getCapacity(), 16);
    }
  }

  @Test
  public void testExpandWithConfigClass() {
    try (TsingtonDataSource tds = new ConfigClassBasedTsingtonDataSource(QingtongConfig.class)) {
      expand(tds);
      assertEquals(tds.getCapacity(), 16);
    }
  }
}
