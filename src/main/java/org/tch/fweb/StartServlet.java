package org.tch.fweb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StartServlet extends HttpServlet
{
  public static final int DEFAULT_PORT = 6708;
  private static File tchForecasterJarFile = null;

  private static int forecasterPort = DEFAULT_PORT;
  private static String forecasterSoftwareUrl = "http://tchforecasttester.org/tch-forecaster.jar";
  private static String jarDirName = null;

  private static StringBuilder startupLog = new StringBuilder();

  private static void logStartup(String s) {
    System.out.println(s);
    startupLog.append(s);
    startupLog.append("\n");
  }

  protected static void logStartup(Throwable throwable) {
    throwable.printStackTrace(System.out);
    throwable.printStackTrace();
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    throwable.printStackTrace(printWriter);
    startupLog.append(printWriter.toString());
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    if (config.getInitParameter("forecaster.software.url") != null) {
      forecasterSoftwareUrl = config.getInitParameter("forecaster.software.url");
      logStartup("Will look TCH Forecast Software at this URL: " + forecasterSoftwareUrl);
    } else {
      logStartup("Will look TCH Forecast Software at this default URL: " + forecasterSoftwareUrl);
    }

    if (config.getInitParameter("forecaster.software.jarDir") != null) {
      jarDirName = config.getInitParameter("forecaster.software.jarDir");
      logStartup("Will place tch-forecaster.jar at this location: " + jarDirName);
    }

    try {
      try {
        downloadJar();
      } catch (Exception e) {
        logStartup(e);
      }
      if (config.getInitParameter("forecaster.port") != null) {
        forecasterPort = Integer.parseInt(config.getInitParameter("forecaster.port"));
        logStartup("Will start TCH Forecast Server on port: " + forecasterPort);
      } else {
        logStartup("Will start TCH Forecast Server on default port: " + forecasterPort);
      }
      startForecaster();
    } catch (Exception e) {
      logStartup(e);
      throw new ServletException("Unable to initialize connection", e);
    }

  }

  public static synchronized void stopForecaster() {
    if (forecastServerObject != null) {
      System.out.println("Shutting down TCH Forecast server");
      try {
        Method closeMethod = forecastServerClass.getMethod("close", null);
        closeMethod.invoke(forecastServerObject, null);
        System.out.println("Shutting down command invoked");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static Class forecastServerClass = null;
  private static Class softwareVersionClass = null;
  private static Object forecastServerObject = null;
  private static String version = "";

  public static synchronized void startForecaster() throws MalformedURLException, ClassNotFoundException,
      InstantiationException, IllegalAccessException, InvocationTargetException {
    tchForecasterJarFile = getJarFile();
    if (tchForecasterJarFile.exists() && forecastServerObject == null) {
      URL jarUrl = tchForecasterJarFile.toURI().toURL();
      URLClassLoader cl = URLClassLoader.newInstance(new URL[] { jarUrl });
      forecastServerClass = cl.loadClass("org.tch.forecast.core.server.ForecastServer");

      Constructor<?>[] allConstructors = forecastServerClass.getConstructors();
      for (Constructor ctor : allConstructors) {
        Class<?>[] pType = ctor.getParameterTypes();
        if (pType.length == 1 && pType[0].equals(int.class)) {
          forecastServerObject = ctor.newInstance(new Integer(forecasterPort));
        }
      }
      if (forecastServerObject == null) {
        logStartup("Forecaster object initilizer not found, unable to start");
      } else {
        try {
          Method startMethod = forecastServerClass.getMethod("start", null);
          startMethod.invoke(forecastServerObject, null);
          logStartup("Forecaster started");
        } catch (NoSuchMethodException nsme) {
          logStartup("Unable to start forecaster");
          logStartup(nsme);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            System.out.println("JVM is being shutdown");
            StartServlet.stopForecaster();
          }
        });
      }

      softwareVersionClass = cl.loadClass("org.tch.forecast.core.SoftwareVersion");

      try {
        Field versionField = softwareVersionClass.getDeclaredField("VERSION");
        version = (String) versionField.get(null);
      } catch (NoSuchFieldException nsfe) {
        logStartup("Unable to get forecaster version");
        logStartup(nsfe);
      }

    } else {
      logStartup("Unable to start the TCH Forecaster");
      if (!tchForecasterJarFile.exists()) {
        logStartup("  + TCH Forecaster (tch-forecaster.jar) does not exist or could not be found");
      }
      if (forecastServerObject != null) {
        logStartup("  + TCH Forecaster software is already running");
      }
    }
  }

  private void downloadJar() throws IOException {
    HttpURLConnection urlConn;
    URL url = new URL(forecasterSoftwareUrl);
    urlConn = (HttpURLConnection) url.openConnection();
    urlConn.setRequestMethod("GET");
    urlConn.setDoInput(true);
    urlConn.setDoOutput(true);
    urlConn.setUseCaches(false);
    urlConn.setReadTimeout(30 * 1000); // set a 30 second timeout

    InputStream inputStream = urlConn.getInputStream();

    File file = getJarFile();
    FileOutputStream fOut = new FileOutputStream(file);
    byte[] buff = new byte[1000];

    int count;
    while ((count = inputStream.read(buff)) != -1) {
      fOut.write(buff, 0, count);
    }
    fOut.close();
    inputStream.close();
    System.out.println("Downloaded latest TCH Forecast Software and saved here: " + file.getAbsolutePath());
  }

  public static File getJarFile() {
    File file = null;
    if (jarDirName == null) {
      file = new File("tch-forecaster.jar");
    } else {
      file = new File(jarDirName);
      file = new File(file, "tch-forecaster.jar");
    }
    return file;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    resp.setContentType("text/html");
    out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">");
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>TCH Forecaster</title>");
    out.println("  </head>");
    out.println("  <body>");
    out.println("    <h1>TCH Forecaster</h1>");
    out.println("    <p>Version " + version + "</p>");
    out.println("    <h2>Web Container Startup Log</h2>");
    out.println("    <pre>");
    out.print(startupLog.toString());
    out.println("    </pre>");
    out.println("    <h2>Forecaster Startup Log</h2>");
    out.println("    <pre>");
    if (forecastServerObject != null) {
      try {
        Method getProcessLogMethod = forecastServerClass.getMethod("getProcessLog", null);
        out.println(getProcessLogMethod.invoke(forecastServerObject, null));
      } catch (Exception e) {
        out.println("Unable to get log from forecast server: " + e.getMessage());
        e.printStackTrace(out);
      }
    } else {
      out.println("Forecaster server not available.");
    }
    out.println("    </pre>");

    //
    out.println("  </body>");
    out.println("</html>");
    out.close();

  }
}
