package com.penyo.tsington.util;

/**
 * 关系型数据库产品
 *
 * @author Penyo
 */
public enum SqlDbProduct {
  POSTGRESQL("org.postgresql.Driver"), MYSQL("com.mysql.cj.jdbc.Driver"), SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver"), ORACLE("oracle.jdbc.driver.OracleDriver");

  private final String driverClassName;

  SqlDbProduct(String driverClassName) {
    this.driverClassName = driverClassName;
  }

  /**
   * 获取驱动类名。
   */
  public String getDriverClassName() {
    return driverClassName;
  }
}
