package com.penyo.tsington.v0;

import com.penyo.tsington.config.PerformanceConfig;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 压力监视器
 *
 * <p>
 * 压力监视器能够实时监控连接池的压力，并智能调用池的扩张/收缩方法。
 * </p>
 *
 * @author Penyo
 */
public class PressureMonitor implements AutoCloseable {
  /**
   * 请求记录
   */
  private final Queue<Instant> requests = new ConcurrentLinkedQueue<>();
  /**
   * 检查线程
   */
  private final Thread monitor;

  public PressureMonitor(TsingtonDataSource monitored) {
    monitor = new Thread(() -> {
      while (true) {
        PerformanceConfig pc = monitored.getPerformanceConfig();
        double pressure = requests.size() / (pc.getScanCycle() / 1000.0);

        if (monitored.isBusy() && pressure > pc.getPressureToExpand()) monitored.expand(pc.getResizeNum());
        else if (monitored.isLeisurely() && pressure < pc.getPressureToContract())
          monitored.contract(pc.getResizeNum());

        requests.clear();
        try {
          synchronized (this) {
            wait(pc.getScanCycle());
          }
        } catch (InterruptedException ignored) {
        }
      }
    });
    monitor.start();
  }

  /**
   * 标记一次请求。
   */
  public void request() {
    requests.offer(Instant.now());
  }

  @Override
  public void close() {
    monitor.interrupt();
  }
}
