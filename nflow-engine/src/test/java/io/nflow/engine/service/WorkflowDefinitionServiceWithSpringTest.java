package io.nflow.engine.service;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.AbstractResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import io.nflow.engine.internal.config.NFlow;
import io.nflow.engine.internal.dao.ArchiveDao;
import io.nflow.engine.internal.dao.ExecutorDao;
import io.nflow.engine.internal.dao.HealthCheckDao;
import io.nflow.engine.internal.dao.StatisticsDao;
import io.nflow.engine.internal.dao.WorkflowDefinitionDao;
import io.nflow.engine.internal.dao.WorkflowInstanceDao;
import io.nflow.engine.internal.workflow.WorkflowInstancePreProcessor;
import io.nflow.engine.workflow.definition.AbstractWorkflowDefinition;
import io.nflow.engine.workflow.definition.WorkflowState;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("nflow-engine-test")
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext
public class WorkflowDefinitionServiceWithSpringTest {

  @Configuration
  @Profile("nflow-engine-test")
  @ComponentScan(basePackageClasses = SpringDummyTestWorkflow.class)
  static class ContextConfiguration {
    @Bean
    public Environment env() {
      return new MockEnvironment().withProperty("nflow.definition.persist", "true");
    }

    @Bean
    @NFlow
    public AbstractResource nflowNonSpringWorkflowsListing() {
      return null;
    }

    @Bean
    public WorkflowInstanceDao workflowInstanceDao() {
      return mock(WorkflowInstanceDao.class);
    }

    @Bean
    public WorkflowDefinitionDao workflowDefinitionDao() {
      return mock(WorkflowDefinitionDao.class);
    }

    @Bean
    public ExecutorDao executorDao() {
      return mock(ExecutorDao.class);
    }

    @Bean
    public StatisticsDao statisticsDao() {
      return mock(StatisticsDao.class);
    }

    @Bean
    public WorkflowInstancePreProcessor preProcessor() {
      return mock(WorkflowInstancePreProcessor.class);
    }

    @Bean
    public ArchiveDao archiveDao() {
      return mock(ArchiveDao.class);
    }

    @Bean
    public HealthCheckDao healthCheckDao() {
      return mock(HealthCheckDao.class);
    }
  }

  @Autowired
  private WorkflowDefinitionService service;

  @Test
  public void springWorkflowDefinitionsAreDetected() {
    List<AbstractWorkflowDefinition<? extends WorkflowState>> definitions = service.getWorkflowDefinitions();
    assertThat(definitions.size(), is(equalTo(1)));
    assertThat(definitions.get(0).getType(), is(new SpringDummyTestWorkflow().getType()));
  }
}
