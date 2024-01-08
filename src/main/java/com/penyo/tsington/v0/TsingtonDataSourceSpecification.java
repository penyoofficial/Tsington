package com.penyo.tsington.v0;

import com.penyo.tsington.config.PerformanceConfig;
import com.penyo.tsington.config.UserConfig;

import java.sql.Connection;

/**
 * 青彤™ 连接池规范
 */
public interface TsingtonDataSourceSpecification extends AutoCloseable {
  /**
   * 获取用户配置。
   */
  UserConfig getUserConfig();

  /**
   * 获取性能配置。
   */
  PerformanceConfig getPerformanceConfig();

  /**
   * 获取可用资源数。
   */
  int getRemainingCapacity();

  /**
   * 获取资源总数。
   */
  int getCapacity();

  /**
   * 检查连接池是否繁忙。
   *
   * <p>
   * 当一个连接池<b>繁忙</b>的时候，意味着可用资源已耗尽。
   * </p>
   *
   * @see TsingtonDataSourceSpecification#isLeisurely()
   */
  default boolean isBusy() {
    return getRemainingCapacity() == 0;
  }

  /**
   * 检查连接池是否悠闲。
   *
   * <p>
   * 当一个连接池<b>悠闲</b>的时候，意味着可用资源超越总量的半数。
   * </p>
   *
   * @see TsingtonDataSourceSpecification#isBusy()
   */
  default boolean isLeisurely() {
    return (double) getRemainingCapacity() / getCapacity() >= 0.5;
  }

  /**
   * 借用连接。
   */
  Connection getConnection();
}
