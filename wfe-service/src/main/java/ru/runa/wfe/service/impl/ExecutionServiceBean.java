package ru.runa.wfe.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.interceptor.Interceptors;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import ru.runa.wfe.audit.ProcessLogFilter;
import ru.runa.wfe.commons.Utils;
import ru.runa.wfe.definition.dto.WfDefinition;
import ru.runa.wfe.definition.logic.ProcessDefinitionLogic;
import ru.runa.wfe.execution.ProcessFilter;
import ru.runa.wfe.execution.dto.RestoreProcessStatus;
import ru.runa.wfe.execution.dto.WfProcess;
import ru.runa.wfe.execution.dto.WfSwimlane;
import ru.runa.wfe.execution.dto.WfToken;
import ru.runa.wfe.execution.logic.ExecutionLogic;
import ru.runa.wfe.graph.view.NodeGraphElement;
import ru.runa.wfe.job.dto.WfJob;
import ru.runa.wfe.lang.ParsedProcessDefinition;
import ru.runa.wfe.presentation.BatchPresentation;
import ru.runa.wfe.presentation.BatchPresentationFactory;
import ru.runa.wfe.service.decl.ExecutionServiceLocal;
import ru.runa.wfe.service.decl.ExecutionServiceRemote;
import ru.runa.wfe.service.decl.ExecutionWebServiceRemote;
import ru.runa.wfe.service.interceptors.EjbExceptionSupport;
import ru.runa.wfe.service.interceptors.EjbTransactionSupport;
import ru.runa.wfe.service.interceptors.PerformanceObserver;
import ru.runa.wfe.service.jaxb.StringKeyValue;
import ru.runa.wfe.service.jaxb.StringKeyValueConverter;
import ru.runa.wfe.service.jaxb.Variable;
import ru.runa.wfe.service.jaxb.VariableConverter;
import ru.runa.wfe.service.utils.FileVariablesUtil;
import ru.runa.wfe.springframework4.ejb.interceptor.SpringBeanAutowiringInterceptor;
import ru.runa.wfe.user.Executor;
import ru.runa.wfe.user.User;
import ru.runa.wfe.var.dto.WfVariable;
import ru.runa.wfe.var.dto.WfVariableHistoryState;
import ru.runa.wfe.var.file.FileVariable;
import ru.runa.wfe.var.file.FileVariableImpl;
import ru.runa.wfe.var.logic.VariableLogic;

@Stateless(name = "ExecutionServiceBean")
@TransactionManagement(TransactionManagementType.BEAN)
@Interceptors({ EjbExceptionSupport.class, PerformanceObserver.class, EjbTransactionSupport.class, SpringBeanAutowiringInterceptor.class })
@WebService(name = "ExecutionAPI", serviceName = "ExecutionWebService")
@SOAPBinding
public class ExecutionServiceBean implements ExecutionServiceLocal, ExecutionServiceRemote, ExecutionWebServiceRemote {
    @Autowired
    private ProcessDefinitionLogic processDefinitionLogic;
    @Autowired
    private ExecutionLogic executionLogic;
    @Autowired
    private VariableLogic variableLogic;

    @WebMethod(exclude = true)
    @Override
    public Long startProcess(@NonNull User user, @NonNull String definitionName, Map<String, Object> variables) {
        FileVariablesUtil.unproxyFileVariables(user, processDefinitionLogic.getLatestProcessDefinition(user, definitionName).getId(), variables);
        return executionLogic.startProcess(user, definitionName, variables);
    }

    @WebMethod(exclude = true)
    @Override
    public Long startProcessById(@NonNull User user, @NonNull Long definitionId, Map<String, Object> variables) {
        FileVariablesUtil.unproxyFileVariables(user, definitionId, variables);
        return executionLogic.startProcess(user, definitionId, variables);
    }

    @Override
    @WebResult(name = "result")
    public Long startProcessWS(@WebParam(name = "user") User user, @WebParam(name = "definitionName") String definitionName,
            @WebParam(name = "variables") List<Variable> variables) {
        WfDefinition definition = processDefinitionLogic.getLatestProcessDefinition(user, definitionName);
        ParsedProcessDefinition parsedProcessDefinition = executionLogic.getDefinition(definition.getId());
        return startProcess(user, definitionName, VariableConverter.unmarshal(parsedProcessDefinition, variables));
    }

    @Override
    @WebResult(name = "result")
    public int getProcessesCount(
            @WebParam(name = "user") @NonNull User user,
            @WebParam(name = "batchPresentation") BatchPresentation batchPresentation
    ) {
        if (batchPresentation == null) {
            batchPresentation = BatchPresentationFactory.CURRENT_PROCESSES.createNonPaged();
        }
        return executionLogic.getProcessesCount(user, batchPresentation);
    }

    @Override
    @WebResult(name = "result")
    public List<WfProcess> getProcesses(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "batchPresentation") BatchPresentation batchPresentation) {
        if (batchPresentation == null) {
            batchPresentation = BatchPresentationFactory.CURRENT_PROCESSES.createNonPaged();
        }
        return executionLogic.getProcesses(user, batchPresentation);
    }

    @Override
    @WebResult(name = "result")
    public WfProcess getProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        return executionLogic.getProcess(user, processId);
    }

    @Override
    @WebResult(name = "result")
    public WfProcess getParentProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        return executionLogic.getParentProcess(user, processId);
    }

    @Override
    @WebResult(name = "result")
    public List<WfProcess> getSubprocesses(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "recursive") boolean recursive) {
        return executionLogic.getSubprocesses(user, processId, recursive);
    }

    @WebMethod(exclude = true)
    @Override
    public List<WfVariable> getVariables(@NonNull User user, @NonNull Long processId) {
        List<WfVariable> list = variableLogic.getVariables(user, processId);
        for (WfVariable variable : list) {
            FileVariablesUtil.proxyFileVariables(user, processId, variable);
        }
        return list;
    }

    @WebMethod(exclude = true)
    @Override
    public Map<Long, List<WfVariable>> getVariables(@NonNull User user, @NonNull List<Long> processIds) {
        Map<Long, List<WfVariable>> result = variableLogic.getVariables(user, processIds);
        for (Map.Entry<Long, List<WfVariable>> entry : result.entrySet()) {
            for (WfVariable variable : entry.getValue()) {
                FileVariablesUtil.proxyFileVariables(user, entry.getKey(), variable);
            }
        }
        return result;
    }

    @WebMethod(exclude = true)
    @Override
    public WfVariableHistoryState getHistoricalVariables(@NonNull User user, @NonNull ProcessLogFilter filter) {
        long processId = filter.getProcessId();
        WfVariableHistoryState result = variableLogic.getHistoricalVariables(user, filter);
        for (WfVariable variable : result.getVariables()) {
            FileVariablesUtil.proxyFileVariables(user, processId, variable);
        }
        return result;
    }

    @WebMethod(exclude = true)
    @Override
    public WfVariableHistoryState getHistoricalVariables(@NonNull User user, @NonNull Long processId, Long taskId) {
        WfVariableHistoryState result = variableLogic.getHistoricalVariables(user, processId, taskId);
        for (WfVariable variable : result.getVariables()) {
            FileVariablesUtil.proxyFileVariables(user, processId, variable);
        }
        return result;
    }

    @Override
    @WebResult(name = "result")
    public List<Variable> getVariablesWS(@WebParam(name = "user") User user, @WebParam(name = "processId") Long processId) {
        List<WfVariable> variables = getVariables(user, processId);
        return VariableConverter.marshal(variables);
    }

    @WebMethod(exclude = true)
    @Override
    public WfVariable getVariable(@NonNull User user, @NonNull Long processId, @NonNull String variableName) {
        WfVariable variable = variableLogic.getVariable(user, processId, variableName);
        FileVariablesUtil.proxyFileVariables(user, processId, variable);
        return variable;
    }

    @Override
    @WebResult(name = "result")
    public Variable getVariableWS(@WebParam(name = "user") User user, @WebParam(name = "processId") Long processId,
            @WebParam(name = "variableName") String variableName) {
        WfVariable variable = getVariable(user, processId, variableName);
        if (variable != null) {
            return VariableConverter.marshal(variable.getDefinition(), variable.getValue());
        }
        return null;
    }

    @WebMethod(exclude = true)
    @Override
    public WfVariable getTaskVariable(@NonNull User user, @NonNull Long processId, @NonNull Long taskId, @NonNull String variableName) {
        WfVariable variable = variableLogic.getTaskVariable(user, taskId, variableName);
        FileVariablesUtil.proxyFileVariables(user, processId, variable);
        return variable;
    }

    @Override
    @WebResult(name = "result")
    public FileVariableImpl getFileVariableValue(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "variableName") @NonNull String variableName) {
        WfVariable variable = variableLogic.getVariable(user, processId, variableName);
        if (variable != null) {
            FileVariable fileVariable = (FileVariable) variable.getValue();
            return new FileVariableImpl(fileVariable);
        }
        return null;
    }

    @WebMethod(exclude = true)
    @Override
    public void updateVariables(@NonNull User user, @NonNull Long processId, @NonNull Map<String, Object> variables) {
        FileVariablesUtil.unproxyFileVariables(user, processId, null, variables);
        variableLogic.updateVariables(user, processId, variables);
    }

    @Override
    @WebResult(name = "result")
    public void cancelProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        executionLogic.cancelProcess(user, processId);
    }

    @Override
    @WebResult(name = "result")
    public RestoreProcessStatus restoreProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        return executionLogic.restoreProcess(user, processId);
    }

    @Override
    @WebResult(name = "result")
    public List<WfSwimlane> getProcessSwimlanes(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        return executionLogic.getProcessSwimlanes(user, processId);
    }
    
    @Override
    @WebResult(name = "result")
    public List<WfSwimlane> getActiveProcessesSwimlanes(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "namePattern") @NonNull String namePattern) {
        return executionLogic.getActiveProcessesSwimlanes(user, namePattern);
    }
    
    @Override
    @WebResult(name = "result")
    public boolean reassignSwimlane(@WebParam(name = "user") User user, @WebParam(name = "id") @NonNull Long id) {
        return executionLogic.reassignSwimlane(user, id);
    }

    @Override
    @WebResult(name = "result")
    public void assignSwimlane(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "swimlaneName") @NonNull String swimlaneName, @WebParam(name = "executor") Executor executor) {
        executionLogic.assignSwimlane(user, processId, swimlaneName, executor);
    }

    @Override
    @WebResult(name = "result")
    public byte[] getProcessDiagram(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "taskId") Long taskId, @WebParam(name = "childProcessId") Long childProcessId,
            @WebParam(name = "subprocessId") String subprocessId) {
        return executionLogic.getProcessDiagram(user, processId, taskId, childProcessId, subprocessId);
    }

    @Override
    @WebResult(name = "result")
    public List<NodeGraphElement> getProcessDiagramElements(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "processId") @NonNull Long processId, @WebParam(name = "subprocessId") String subprocessId) {
        return executionLogic.getProcessDiagramElements(user, processId, subprocessId);
    }

    @Override
    @WebResult(name = "result")
    public NodeGraphElement getProcessDiagramElement(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "processId") @NonNull Long processId, @WebParam(name = "nodeId") @NonNull String nodeId) {
        return executionLogic.getProcessDiagramElement(user, processId, nodeId);
    }

    @Override
    @WebResult(name = "result")
    public void removeProcesses(@WebParam(name = "user") @NonNull User user, @WebParam @NonNull ProcessFilter filter) {
        executionLogic.deleteProcesses(user, filter);
    }

    @Override
    @WebResult(name = "result")
    public boolean upgradeProcessToDefinitionVersion(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "processId") @NonNull Long processId, @WebParam(name = "version") Long version) {
        return executionLogic.upgradeProcessToDefinitionVersion(user, processId, version);
    }

    @Override
    @WebResult(name = "result")
    public int upgradeProcessesToDefinitionVersion(
            @WebParam(name = "user") @NonNull User user,
            @WebParam(name = "definitionId") @NonNull Long processDefinitionId,
            @WebParam(name = "version") @NonNull Long newVersion
    ) {
        return executionLogic.upgradeProcessesToDefinitionVersion(user, processDefinitionId, newVersion);
    }

    @Override
    @WebResult(name = "result")
    public void updateVariablesWS(@WebParam(name = "user") User user, @WebParam(name = "processId") Long processId,
            @WebParam(name = "variables") List<Variable> variables) {
        WfProcess process = executionLogic.getProcess(user, processId);
        ParsedProcessDefinition parsedProcessDefinition = executionLogic.getDefinition(process.getDefinitionId());
        updateVariables(user, processId, VariableConverter.unmarshal(parsedProcessDefinition, variables));
    }

    @Override
    @WebResult(name = "result")
    public List<WfJob> getProcessJobs(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "recursive") boolean recursive) {
        return executionLogic.getJobs(user, processId, recursive);
    }

    @Override
    @WebResult(name = "result")
    public List<WfToken> getProcessTokens(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId,
            @WebParam(name = "recursive") boolean recursive) {
        return executionLogic.getTokens(user, processId, recursive, false);
    }

    @Override
    @WebResult(name = "result")
    public boolean activateProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        return executionLogic.activateProcess(user, processId);
    }

    @Override
    @WebResult(name = "result")
    public void suspendProcess(@WebParam(name = "user") @NonNull User user, @WebParam(name = "processId") @NonNull Long processId) {
        executionLogic.suspendProcess(user, processId);
    }

    @Override
    @WebMethod(exclude = true)
    public void sendSignal(@NonNull User user, @NonNull Map<String, String> routingData, @NonNull Map<String, ?> payloadData, long ttlInSeconds) {
        Utils.sendBpmnMessage(routingData, payloadData, ttlInSeconds * 1000);
    }

    @WebResult(name = "result")
    public void sendSignalWS(@WebParam(name = "user") @NonNull User user, @WebParam(name = "routingData") @NonNull List<StringKeyValue> routingData,
            @WebParam(name = "payloadData") List<StringKeyValue> payloadData, @WebParam(name = "ttlInSeconds") long ttlInSeconds) {
        sendSignal(user,
                StringKeyValueConverter.unmarshal(routingData), 
                StringKeyValueConverter.unmarshal(payloadData), 
 ttlInSeconds);
    }

    @Override
    @WebMethod(exclude = true)
    public boolean signalReceiverIsActive(@NonNull User user, @NonNull Map<String, String> routingData) {
        return !executionLogic.findTokensForMessageSelector(routingData).isEmpty();
    }

    @WebResult(name = "result")
    public boolean signalReceiverIsActiveWS(@WebParam(name = "user") @NonNull User user,
            @WebParam(name = "routingData") @NonNull List<StringKeyValue> routingData) {
        return signalReceiverIsActive(user, StringKeyValueConverter.unmarshal(routingData));
    }

    @Override
    @WebResult(name = "result")
    public Set<Executor> getAllExecutorsByProcessId(User user, Long processId, boolean expandGroups) {
        return executionLogic.getAllExecutorsByProcessId(user, processId, expandGroups);
    }
}
