package com.nitorcreations.nflow.rest.v0.converter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.stereotype.Component;

import com.nitorcreations.nflow.engine.workflow.WorkflowDefinition;
import com.nitorcreations.nflow.engine.workflow.WorkflowState;
import com.nitorcreations.nflow.rest.v0.msg.ListWorkflowDefinitionResponse;
import com.nitorcreations.nflow.rest.v0.msg.ListWorkflowDefinitionResponse.State;

@Component
public class ListWorkflowDefinitionConverter {

  public ListWorkflowDefinitionResponse convert(WorkflowDefinition<? extends WorkflowState> definition) {
    ListWorkflowDefinitionResponse resp = new ListWorkflowDefinitionResponse();
    resp.type = definition.getType();
    resp.name = definition.getName();
    resp.description = definition.getDescription();
    resp.onError = definition.getErrorState().name();
    Map<String, State> states = new HashMap<>();
    for (WorkflowState state : definition.getStates()) {
      states.put(state.name(), new State(state.name(), state.getType().name()));
    }
    for (Entry<String,String> entry : definition.getAllowedTransitions().entrySet()) {
      State state = states.get(entry.getKey());
      state.transitions.add(entry.getValue());
    }
    for (Entry<String,String> entry : definition.getFailureTransitions().entrySet()) {
      State state = states.get(entry.getKey());
      state.onFailure = entry.getValue();
    }
    resp.states = states.values().toArray(new State[0]);
    return resp;
  }

}