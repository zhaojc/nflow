package io.nflow.engine.internal.executor;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.nflow.engine.internal.dao.ExecutorDao;
import io.nflow.engine.internal.dao.PollingRaceConditionException;
import io.nflow.engine.internal.dao.WorkflowInstanceDao;
import io.nflow.engine.internal.util.PeriodicLogger;

@Component
@SuppressFBWarnings(value = "MDM_RANDOM_SEED", justification = "rand does not need to be secure")
public class WorkflowDispatcher implements Runnable {

  private static final Logger logger = getLogger(WorkflowDispatcher.class);
  private static final PeriodicLogger periodicLogger = new PeriodicLogger(logger, 60);

  private volatile boolean shutdownRequested;
  private final CountDownLatch shutdownDone = new CountDownLatch(1);

  private final WorkflowInstanceExecutor executor;
  private final WorkflowInstanceDao workflowInstances;
  private final WorkflowStateProcessorFactory stateProcessorFactory;
  private final ExecutorDao executorDao;
  private final long sleepTimeMillis;
  private final int stuckThreadThresholdSeconds;
  private final Random rand = new Random();

  @Inject
  @SuppressFBWarnings(value = "WEM_WEAK_EXCEPTION_MESSAGING", justification = "Transaction support exception message is fine")
  public WorkflowDispatcher(WorkflowInstanceExecutor executor, WorkflowInstanceDao workflowInstances,
      WorkflowStateProcessorFactory stateProcessorFactory, ExecutorDao executorDao, Environment env) {
    this.executor = executor;
    this.workflowInstances = workflowInstances;
    this.stateProcessorFactory = stateProcessorFactory;
    this.executorDao = executorDao;
    this.sleepTimeMillis = env.getRequiredProperty("nflow.dispatcher.sleep.ms", Long.class);
    this.stuckThreadThresholdSeconds = env.getRequiredProperty("nflow.executor.stuckThreadThreshold.seconds", Integer.class);
    if (!executorDao.isTransactionSupportEnabled()) {
      throw new BeanCreationException("Transaction support must be enabled");
    }
  }

  @Override
  public void run() {
    logger.info("Starting.");
    try {
      while (!shutdownRequested) {
        try {
          executor.waitUntilQueueSizeLowerThanThreshold(executorDao.getMaxWaitUntil());

          if (!shutdownRequested) {
            if (executorDao.tick()) {
              workflowInstances.recoverWorkflowInstancesFromDeadNodes();
            }
            int potentiallyStuckProcessors = stateProcessorFactory.getPotentiallyStuckProcessors();
            if (potentiallyStuckProcessors > 0) {
              periodicLogger.warn("{} of {} state processor threads are potentially stuck (processing longer than {} seconds)",
                  potentiallyStuckProcessors, executor.getThreadCount(), stuckThreadThresholdSeconds);
            }
            dispatch(getNextInstanceIds());
          }
        } catch (PollingRaceConditionException pex) {
          logger.info(pex.getMessage());
          sleep(true);
        } catch (@SuppressWarnings("unused") InterruptedException dropThrough) {
        } catch (Exception e) {
          logger.error("Exception in executing dispatcher - retrying after sleep period (" + e.getMessage() + ")", e);
          sleep(false);
        }
      }
    } finally {
      shutdownPool();
      executorDao.markShutdown();
      logger.info("Shutdown finished.");
      shutdownDone.countDown();
    }
  }

  public void shutdown() {
    shutdownRequested = true;
    logger.info("Shutdown requested.");
    try {
      // TODO use timeout?
      shutdownDone.await();
    } catch (@SuppressWarnings("unused") InterruptedException e) {
      logger.info("Shutdown interrupted.");
    }
  }

  private void shutdownPool() {
    try {
      executor.shutdown();
    } catch (Exception e) {
      logger.error("Error in shutting down thread pool.", e);
    }
  }

  private void dispatch(List<Integer> nextInstanceIds) {
    if (nextInstanceIds.isEmpty()) {
      logger.debug("Found no workflow instances, sleeping.");
      sleep(false);
      return;
    }
    logger.debug("Found {} workflow instances, dispatching executors.", nextInstanceIds.size());
    for (Integer instanceId : nextInstanceIds) {
      executor.execute(stateProcessorFactory.createProcessor(instanceId));
    }
  }

  private List<Integer> getNextInstanceIds() {
    int nextBatchSize = executor.getQueueRemainingCapacity();
    logger.debug("Polling next {} workflow instances.", nextBatchSize);
    return workflowInstances.pollNextWorkflowInstanceIds(nextBatchSize);
  }

  @SuppressFBWarnings(value = "MDM_THREAD_YIELD", justification = "Intentionally masking race condition")
  private void sleep(boolean randomize) {
    try {
      if (randomize) {
        Thread.sleep((long) (sleepTimeMillis * rand.nextDouble()));
      } else {
        Thread.sleep(sleepTimeMillis);
      }
    } catch (@SuppressWarnings("unused") InterruptedException ok) {
    }
  }
}
