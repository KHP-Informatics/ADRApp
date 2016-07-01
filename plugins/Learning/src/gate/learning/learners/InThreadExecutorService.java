package gate.learning.learners;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplest possible executor service that just runs the tasks in the
 * calling thread.
 */
public class InThreadExecutorService extends AbstractExecutorService {

  private boolean hasShutdown = false;

  public boolean awaitTermination(long timeout, TimeUnit unit)
          throws InterruptedException {
    return true;
  }

  public boolean isShutdown() {
    return hasShutdown;
  }

  public boolean isTerminated() {
    return hasShutdown;
  }

  public void shutdown() {
    this.hasShutdown = true;
  }

  public List<Runnable> shutdownNow() {
    this.hasShutdown = true;
    return null;
  }

  public void execute(Runnable command) {
    command.run();
  }

}
