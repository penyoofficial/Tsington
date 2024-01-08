package com.penyo.tsington.v0;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * 可追踪连接
 *
 * <p>
 * 可追踪连接是对 {@link java.sql.Connection Connection} 的装饰。用户可从 {@link
 * com.penyo.tsington.v0.TsingtonDataSource TsingtonDataSource} 获得。
 * 使用结束后，自然关闭即可。
 * </p>
 *
 * @author Penyo
 */
public class TrackableConnection implements AutoCloseable, InvocationHandler {
  /**
   * 唯一识别码
   */
  private final int id;
  /**
   * 连接实例
   */
  private final Connection connection;
  /**
   * 从属连接池实例
   */
  private final TsingtonDataSource tsingtonDataSource;
  /**
   * 连接占用状态
   */
  private boolean isAvailable = true;

  protected TrackableConnection(Connection connection, TsingtonDataSource tsingtonDataSource) {
    id = connection.hashCode() + Instant.now().hashCode();
    this.connection = connection;
    this.tsingtonDataSource = tsingtonDataSource;
  }

  /**
   * 获取代理。
   */
  public Connection proxy() {
    return (Connection) Proxy.newProxyInstance(connection.getClass().getClassLoader(), connection.getClass().getInterfaces(), this);
  }

  /**
   * 获取真的连接。
   */
  protected Connection getRealConnection() {
    return connection;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (!isAvailable) throw new IllegalStateException("The called entity has been recycled.");
    if (method.getName().equals("close")) {
      close();
      return null;
    }
    return method.invoke(connection, args);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TrackableConnection that = (TrackableConnection) o;
    return id == that.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public void close() {
    isAvailable = false;
    try {
      if (!connection.isClosed()) tsingtonDataSource.returnConnection(this);
    } catch (SQLException ignored) {
    }
  }

  /**
   * “夺舍”。
   */
  protected void tradeOff() {
    close();
    try {
      connection.close();
    } catch (SQLException ignored) {
    }
  }
}
