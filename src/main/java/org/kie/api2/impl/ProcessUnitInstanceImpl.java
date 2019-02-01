package org.kie.api2.impl;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.drools.core.SessionConfiguration;
import org.drools.core.SessionConfigurationImpl;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.impl.EnvironmentImpl;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.phreak.PropagationEntry;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.EntryPointId;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.runtime.process.ProcessRuntimeFactory;
import org.drools.core.runtime.process.ProcessRuntimeFactoryService;
import org.drools.core.spi.Activation;
import org.drools.core.spi.AsyncExceptionHandler;
import org.drools.core.spi.FactHandleFactory;
import org.drools.core.spi.GlobalResolver;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.JDKTimerService;
import org.drools.core.util.bitmask.BitMask;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.KieBase;
import org.kie.api.definition.process.Process;
import org.kie.api.event.kiebase.KieBaseEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.logger.KieRuntimeLogger;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.api2.api.Kie;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstance;
import org.kie.api2.api.Unit;

/**
 * It represents a process with all its runtime-related state.
 * It's basically a ProcessInstance + additional meta data.
 */
public class ProcessUnitInstanceImpl<U extends ProcessUnit> implements ProcessUnitInstance<U> {

    private final U unit;
    private final InternalProcessRuntime processRuntime;
    private final Process process;
    private final ProcessUnitDummyWorkingMemory dummyWorkingMemory;
    private final Kie.Runtime runtime;
    private WorkflowProcessInstanceImpl processInstance;

    public ProcessUnitInstanceImpl(U unit, Kie.Runtime runtime) {
        this.runtime = runtime;
        // I know this isn't correct: for now we are recreating new factories and runtimes each time; this is only for this PoC
        // see the Unit design document for details of how this will be actually handled using managers
        ProcessRuntimeFactoryService svc = ProcessRuntimeFactory.getProcessRuntimeFactoryService();
        this.unit = unit;
        this.dummyWorkingMemory = new ProcessUnitDummyWorkingMemory(runtime);
        this.processRuntime = svc.newProcessRuntime(dummyWorkingMemory);
        // by convention (for now) we assume process id == unit name
        this.process = runtime.kieBase().getProcess(id());
        this.dummyWorkingMemory.setProcessRuntime(processRuntime);

        this.processInstance = null;
    }

    @Override
    public void run() {
        // we convert back and forth between the fields of the unit and a map.
        // This is suboptimal, but it's just for the PoC. We can think of better ways,
        // like creating a Map that delegates to the Unit fields (I have already done it and kept in a PR for a while)
        HashMap<String, Object> params = new HashMap<>();
        // extract the params from the Unit and pass it via Map (pick each field and set it as key for now)
        toMap(unit, params);
        processInstance = (WorkflowProcessInstanceImpl) processRuntime.createProcessInstance(
                unit.getClass().getCanonicalName(), params);
        processRuntime.startProcessInstance(processInstance.getId());
        // reconcile unit with internal variable representation
        fromMap(processInstance.getVariables(), unit);
    }

    private static void toMap(Unit unit, Map<String, Object> map) {
        try {
            for (Field field : unit.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                String key = field.getName();
                Object value = field.get(unit);
                map.put(key, value);
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException();
        }
    }

    private static void fromMap(Map<String, Object> map, Unit unit) {
        try {
            for (Field field : unit.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                String key = field.getName();
                field.set(unit, map.get(key));
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public U unit() {
        return unit;
    }

    public String id() {
        return unit.getClass().getCanonicalName();
    }
}

/**
 * A severely limited implementation of the WorkingMemory interface.
 * It only exists for legacy reasons.
 */
class ProcessUnitDummyWorkingMemory implements InternalWorkingMemory,
                                               InternalKnowledgeRuntime {

    private final TimerService timerService;
    private final EnvironmentImpl environment;
    private final Kie.Runtime runtime;
    private InternalProcessRuntime processRuntime;

    ProcessUnitDummyWorkingMemory(Kie.Runtime runtime) {
        this.runtime = runtime;
        environment = new EnvironmentImpl();
        timerService = new JDKTimerService();
//        // must use (InternalWorkingMemory) and not (InternalKnowledgeRuntime) otherwise it breaks with:
//        // java.lang.ClassCastException: org.drools.core.common.ProjectClassLoader cannot be cast to org.kie.internal.utils.CompositeClassLoader
    }

    void setProcessRuntime(InternalProcessRuntime processRuntime) {
        this.processRuntime = processRuntime;
    }

    public Kie.Runtime runtime() {
        return runtime;
    }

    @Override
    public InternalAgenda getAgenda() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getIdentifier() {
        return 0;
    }

    @Override
    public void setIdentifier(long id) {

    }

    @Override
    public void setEndOperationListener(EndOperationListener listener) {

    }

    @Override
    public long getLastIdleTimestamp() {
        return 0;
    }

    @Override
    public void setRuleRuntimeEventSupport(RuleRuntimeEventSupport workingMemoryEventSupport) {

    }

    @Override
    public void setAgendaEventSupport(AgendaEventSupport agendaEventSupport) {

    }

    @Override
    public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
        return null;
    }

    @Override
    public void clearNodeMemory(MemoryFactory node) {

    }

    @Override
    public NodeMemories getNodeMemories() {
        return null;
    }

    @Override
    public long getNextPropagationIdCounter() {
        return 0;
    }

    @Override
    public ObjectStore getObjectStore() {
        return null;
    }

    @Override
    public void queueWorkingMemoryAction(WorkingMemoryAction action) {

    }

    @Override
    public FactHandleFactory getFactHandleFactory() {
        return null;
    }

    @Override
    public EntryPointId getEntryPoint() {
        return null;
    }

    @Override
    public EntryPointNode getEntryPointNode() {
        return null;
    }

    @Override
    public EntryPoint getEntryPoint(String name) {
        return null;
    }

    @Override
    public FactHandle getFactHandleByIdentity(Object object) {
        return null;
    }

    @Override
    public Lock getLock() {
        return null;
    }

    @Override
    public boolean isSequential() {
        return false;
    }

    @Override
    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return null;
    }

    @Override
    public InternalFactHandle getInitialFactHandle() {
        return null;
    }

    @Override
    public Calendars getCalendars() {
        return null;
    }

    @Override
    public TimerService getTimerService() {
        return timerService;
    }

    @Override
    public InternalKnowledgeRuntime getKnowledgeRuntime() {
        return this;
    }

    @Override
    public Map<String, Channel> getChannels() {
        return null;
    }

    @Override
    public Collection<? extends EntryPoint> getEntryPoints() {
        return null;
    }

    @Override
    public SessionConfiguration getSessionConfiguration() {
        return new SessionConfigurationImpl();
    }

    @Override
    public void startBatchExecution() {

    }

    @Override
    public void endBatchExecution() {

    }

    @Override
    public void startOperation() {

    }

    @Override
    public void endOperation() {

    }

    @Override
    public void executeQueuedActions() {

    }

    @Override
    public long getIdleTime() {
        return 0;
    }

    @Override
    public long getTimeToNextJob() {
        return 0;
    }

    @Override
    public void updateEntryPointsCache() {

    }

    @Override
    public void prepareToFireActivation() {

    }

    @Override
    public void activationFired() {

    }

    @Override
    public long getTotalFactCount() {
        return 0;
    }

    @Override
    public InternalProcessRuntime getProcessRuntime() {
        return this.processRuntime;
    }

    @Override
    public InternalProcessRuntime internalGetProcessRuntime() {
        return null;
    }

    @Override
    public void closeLiveQuery(InternalFactHandle factHandle) {

    }

    @Override
    public void addPropagation(PropagationEntry propagationEntry) {

    }

    @Override
    public void flushPropagations() {

    }

    @Override
    public void activate() {

    }

    @Override
    public void deactivate() {

    }

    @Override
    public boolean tryDeactivate() {
        return false;
    }

    @Override
    public Iterator<? extends PropagationEntry> getActionsIterator() {
        return null;
    }

    @Override
    public void removeGlobal(String identifier) {

    }

    @Override
    public void notifyWaitOnRest() {

    }

    @Override
    public void cancelActivation(Activation activation, boolean declarativeAgenda) {

    }

    @Override
    public void setGlobal(String identifier, Object value) {

    }

    @Override
    public Object getGlobal(String identifier) {
        return null;
    }

    @Override
    public Globals getGlobals() {
        return null;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public KieBase getKieBase() {
        return runtime.kieBase();
    }

    @Override
    public void registerChannel(String name, Channel channel) {

    }

    @Override
    public void unregisterChannel(String name) {

    }

    @Override
    public void setGlobalResolver(GlobalResolver globalResolver) {

    }

    @Override
    public GlobalResolver getGlobalResolver() {
        return null;
    }

    @Override
    public InternalKnowledgeBase getKnowledgeBase() {
        return null;
    }

    @Override
    public int fireAllRules() {
        return 0;
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter) {
        return 0;
    }

    @Override
    public int fireAllRules(int fireLimit) {
        return 0;
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        return 0;
    }

    @Override
    public Object getObject(FactHandle handle) {
        return null;
    }

    @Override
    public FactHandle getFactHandle(Object object) {
        return null;
    }

    @Override
    public Iterator<?> iterateObjects() {
        return null;
    }

    @Override
    public Iterator<?> iterateObjects(ObjectFilter filter) {
        return null;
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles() {
        return null;
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles(ObjectFilter filter) {
        return null;
    }

    @Override
    public void setFocus(String focus) {

    }

    @Override
    public QueryResults getQueryResults(String query, Object... arguments) {
        return null;
    }

    @Override
    public LiveQuery openLiveQuery(String query, Object[] arguments, ViewChangedEventListener listener) {
        return null;
    }

    @Override
    public void setAsyncExceptionHandler(AsyncExceptionHandler handler) {

    }

    @Override
    public void clearAgenda() {

    }

    @Override
    public void clearAgendaGroup(String group) {

    }

    @Override
    public void clearActivationGroup(String group) {

    }

    @Override
    public void clearRuleFlowGroup(String group) {

    }

    @Override
    public ProcessInstance startProcess(String processId) {
        return null;
    }

    @Override
    public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return null;
    }

    @Override
    public ProcessInstance createProcessInstance(String processId, Map<String, Object> parameters) {
        return null;
    }

    @Override
    public ProcessInstance startProcessInstance(long processInstanceId) {
        return null;
    }

    @Override
    public void signalEvent(String type, Object event) {

    }

    @Override
    public void signalEvent(String type, Object event, long processInstanceId) {

    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        return null;
    }

    @Override
    public ProcessInstance getProcessInstance(long id) {
        return null;
    }

    @Override
    public ProcessInstance getProcessInstance(long id, boolean readOnly) {
        return null;
    }

    @Override
    public void abortProcessInstance(long processInstanceId) {

    }

    @Override
    public WorkItemManager getWorkItemManager() {
        return null;
    }

    @Override
    public void halt() {

    }

    @Override
    public WorkingMemoryEntryPoint getWorkingMemoryEntryPoint(String id) {
        return null;
    }

    @Override
    public SessionClock getSessionClock() {
        return null;
    }

    @Override
    public FactHandle insert(Object object, boolean dynamic) {
        return null;
    }

    @Override
    public void dispose() {

    }

    @Override
    public void delete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode) {

    }

    @Override
    public void delete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState) {

    }

    @Override
    public void update(FactHandle handle, Object object, BitMask mask, Class<?> modifiedClass, Activation activation) {

    }

    @Override
    public TruthMaintenanceSystem getTruthMaintenanceSystem() {
        return null;
    }

    @Override
    public InternalWorkingMemory getInternalWorkingMemory() {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public FactHandleFactory getHandleFactory() {
        return null;
    }

    @Override
    public void addEventListener(RuleRuntimeEventListener listener) {

    }

    @Override
    public void removeEventListener(RuleRuntimeEventListener listener) {

    }

    @Override
    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        return null;
    }

    @Override
    public void addEventListener(AgendaEventListener listener) {

    }

    @Override
    public void removeEventListener(AgendaEventListener listener) {

    }

    @Override
    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return null;
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return null;
    }

    @Override
    public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
        return null;
    }

    @Override
    public RuleEventListenerSupport getRuleEventSupport() {
        return null;
    }

    @Override
    public void addEventListener(KieBaseEventListener listener) {

    }

    @Override
    public void removeEventListener(KieBaseEventListener listener) {

    }

    @Override
    public Collection<KieBaseEventListener> getKieBaseEventListeners() {
        return null;
    }

    @Override
    public String getEntryPointId() {
        return null;
    }

    @Override
    public FactHandle insert(Object object) {
        return null;
    }

    @Override
    public void retract(FactHandle handle) {

    }

    @Override
    public void delete(FactHandle handle) {

    }

    @Override
    public void delete(FactHandle handle, FactHandle.State fhState) {

    }

    @Override
    public void update(FactHandle handle, Object object) {

    }

    @Override
    public void update(FactHandle handle, Object object, String... modifiedProperties) {

    }

    @Override
    public Collection<? extends Object> getObjects() {
        return null;
    }

    @Override
    public Collection<? extends Object> getObjects(ObjectFilter filter) {
        return Collections.emptyList();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles() {
        return null;
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
        return null;
    }

    @Override
    public long getFactCount() {
        return 0;
    }

    @Override
    public KieRuntimeLogger getLogger() {
        return null;
    }

    @Override
    public void addEventListener(ProcessEventListener listener) {

    }

    @Override
    public void removeEventListener(ProcessEventListener listener) {

    }

    @Override
    public Collection<ProcessEventListener> getProcessEventListeners() {
        return null;
    }
}
