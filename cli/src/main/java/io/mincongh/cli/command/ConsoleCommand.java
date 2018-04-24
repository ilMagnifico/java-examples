package io.mincongh.cli.command;

import io.mincongh.cli.ExitCode;
import io.mincongh.cli.FakeLauncher;
import io.mincongh.cli.Messages;
import io.mincongh.cli.option.HasServerOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts fake server in a console mode.
 *
 * <p>Press "CTRL" + "C" will stop it.
 *
 * @author Mincong Huang
 */
public class ConsoleCommand extends Command<Integer> implements HasServerOptions {

  private static final Logger LOGGER = Logger.getLogger(ConsoleCommand.class.getName());

  private ExecutorService executor = Executors.newSingleThreadExecutor(new SimpleThreadFactory());

  private FakeLauncher launcher;

  private LauncherShutdownHook shutdownHook;

  private volatile boolean stopAwait = false;

  public ConsoleCommand(String... args) {
    super(args);
  }

  @Override
  String name() {
    return Commands.CONSOLE;
  }

  @Override
  void validate() {
    // Do nothing.
  }

  @Override
  public Integer call() {
    executor.execute(
        () -> {
          launcher.start();
          shutdownHook = new LauncherShutdownHook();
          Runtime.getRuntime().addShutdownHook(shutdownHook);

          if (!isQuiet()) {
            LOGGER.info("Go to http://localhost:8080");
          }

          await();
          stop();
        });
    return ExitCode.OK;
  }

  private void await() {
    long start = System.currentTimeMillis();
    while (!stopAwait) {
      long duration = System.currentTimeMillis() - start;
      try {
        String msg = duration + " ms";
        LOGGER.info(msg);
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        stopAwait = true;
      }
      if (duration > 15_000) {
        LOGGER.warning("Timeout.");
        stopAwait = true;
      }
    }
  }

  private void stop() {
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    launcher.stop();
  }

  @Override
  public String getHelpMessage() {
    return Messages.consoleCommandDescription();
  }

  public FakeLauncher getLauncher() {
    return launcher;
  }

  public ConsoleCommand setLauncher(FakeLauncher launcher) {
    this.launcher = launcher;
    return this;
  }

  private static class SimpleThreadFactory implements ThreadFactory {

    private static final AtomicInteger COUNT = new AtomicInteger(0);

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "FakeProcessThread-" + COUNT.getAndIncrement());
    }
  }

  /**
   * Shutdown hook which will perform a clean shutdown of Launcher if needed.
   *
   * <p>Similar to Tomcat's CatalinaShutdownHook.
   */
  private class LauncherShutdownHook extends Thread {

    @Override
    public void run() {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("Shutting down...");
      }

      if (getLauncher() != null) {
        launcher.stop();
      }

      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("Shutdown complete.");
      }
    }
  }
}
