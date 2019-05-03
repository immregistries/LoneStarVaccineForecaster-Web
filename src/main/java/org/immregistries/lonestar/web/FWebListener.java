package org.immregistries.lonestar.web;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class FWebListener implements ServletContextListener
{
  public void contextInitialized(ServletContextEvent sce) {
    // nothing to do
  }

  public void contextDestroyed(ServletContextEvent sce) {
    StartServlet.stopForecaster();
  }
}
