package com.penyo.tsington.v0;

import com.penyo.tsington.config.PerformanceConfig;
import com.penyo.tsington.config.UserConfig;
import com.penyo.tsington.util.DriverProxy;

import java.sql.Connection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <h1>青彤™ 连接池</h1>
 *
 * <p>
 * 连接池是一种对 {@link java.sql.Connection Connection} 池化技术的体现。其生命周期分为<b>初始期、
 * 伺服期和终结期</b>三个阶段。实例化池时，其立刻进入初始期，期间池拒绝响应；当驱动和连接集合就绪后，
 * 进入伺服期，此时池可以向外提供有关 {@link TrackableConnection TrackableConnection}
 * 的服务；手动调用池的 {@code shutdown()} 方法后，池进入终结期，其内的连接会被全部销毁，即不再可用。
 * </p>
 *
 * <p>
 * 一般情况下，一个项目只需要一个连接池。因此建议您按照<b>单例模式</b>设计一个代理类，用于存放静态化的池实例，如：
 * </p>
 *
 * <pre>
 * public class TsingtonSingleton {
 *   public static final TsingtonDataSource POOL;
 * }
 * </pre>
 *
 * <p>
 * 初始状态下，连接池会有 8 个最低连接数。当池已见底且对连接的请求比较频繁时，就会触发池的扩张机制；
 * 相反地，如果池长时间有大量空闲连接（远远大于最低连接数），就会触发收缩机制以节省硬件资源。
 * 具体的参数可在 {@link com.penyo.tsington.config.PerformanceConfig TsingtonInside}
 * 中被指定，并传入池。
 * </p>
 *
 * @author Penyo
 */
public abstract class TsingtonDataSource implements TsingtonDataSourceSpecification {
  /**
   * 用户配置
   */
  private UserConfig userConfig;
  /**
   * 性能配置
   */
  private PerformanceConfig performanceConfig;

  @Override
  public UserConfig getUserConfig() {
    return userConfig;
  }

  @Override
  public PerformanceConfig getPerformanceConfig() {
    return performanceConfig;
  }

  /**
   * 压力监视器
   */
  private PressureMonitor pressureMonitor;

  protected TsingtonDataSource() {
  }

  /**
   * 连接池生命周期状态
   */
  private boolean isAlive = false;

  /**
   * 激活连接池。
   */
  protected void activate(UserConfig uc, PerformanceConfig pc) {
    if (uc == null || pc == null || isAlive) throw new RuntimeException();

    DriverProxy.register(uc.driver());
    this.userConfig = uc;
    this.performanceConfig = pc;
    isAlive = true;
    expand(pc.getMinConnectionsNum());
    pressureMonitor = new PressureMonitor(this);
  }

  /**
   * 空闲队列
   */
  private final Queue<TrackableConnection> idles = new ConcurrentLinkedQueue<>();
  /**
   * 忙碌队列
   */
  private final Queue<TrackableConnection> workings = new ConcurrentLinkedQueue<>();

  @Override
  public int getRemainingCapacity() {
    return idles.size();
  }

  @Override
  public int getCapacity() {
    return idles.size() + workings.size();
  }

  /**
   * 扩张连接池。
   */
  protected void expand(int amount) {
    if (!isAlive) throw new RuntimeException();

    if (getRemainingCapacity() + amount <= performanceConfig.getMaxConnectionsNum()) for (int i = 0; i < amount; i++)
      try {
        Connection c = DriverProxy.getConnection(userConfig);
        if (c == null) throw new RuntimeException("Cannot login to SQL server.");
        idles.add(new TrackableConnection(c, this));
      } catch (Exception ignored) {
      }
  }

  /**
   * 收缩连接池。
   */
  protected void contract(int amount) {
    if (!isAlive) throw new RuntimeException();

    if (getRemainingCapacity() - amount >= performanceConfig.getMinConnectionsNum()) for (int i = 0; i < amount; i++) {
      TrackableConnection cs = idles.poll();
      if (cs != null) cs.tradeOff();
    }
  }

  @Override
  public synchronized Connection getConnection() {
    if (!isAlive) throw new RuntimeException();

    pressureMonitor.request();

    TrackableConnection tc = null;

    long requestTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - requestTime < performanceConfig.getRequestTimeout()) {
      if (idles.isEmpty()) try {
        wait(100);
      } catch (InterruptedException ignored) {
      }
      else {
        tc = idles.poll();
        workings.offer(tc);
        break;
      }
    }

    if (tc != null)
      return tc.proxy();
    return null;
  }

  /**
   * 归还连接。
   */
  protected synchronized void returnConnection(TrackableConnection cs) {
    if (!isAlive) throw new RuntimeException();

    if (workings.contains(cs)) {
      workings.remove(cs);
      idles.offer(new TrackableConnection(cs.getRealConnection(), this));
    }
  }

  @Override
  public void close() {
    if (!isAlive) throw new RuntimeException();

    for (TrackableConnection cs : workings)
      try {
        cs.close();
      } catch (Exception ignored) {
      }
    for (TrackableConnection cs : idles)
      try {
        cs.tradeOff();
      } catch (Exception ignored) {
      }

    pressureMonitor.close();
    isAlive = false;
  }
}
