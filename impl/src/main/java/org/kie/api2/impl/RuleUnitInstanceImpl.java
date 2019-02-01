package org.kie.api2.impl;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.drools.core.SessionConfiguration;
import org.drools.core.SessionConfigurationImpl;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.base.MapGlobalResolver;
import org.drools.core.common.ConcurrentNodeMemories;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryActions;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NamedEntryPoint;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.factmodel.traits.Thing;
import org.drools.core.factmodel.traits.TraitableBean;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.phreak.PropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ReteooFactHandleFactory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.EntryPointId;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.spi.Activation;
import org.drools.core.spi.AsyncExceptionHandler;
import org.drools.core.spi.FactHandleFactory;
import org.drools.core.spi.GlobalResolver;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.JDKTimerService;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.event.kiebase.KieBaseEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.internal.runtime.beliefs.Mode;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.time.SessionClock;
import org.kie.api2.api.DataSource;
import org.kie.api2.api.Kie;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.api.RuleUnitInstance;

/**
 * Represents a {@link RuleUnit} with all its runtime-related data. It's the equivalent
 * of a session, but with the working memory, agenda, etc. limited to this only Unit.
 */
public class RuleUnitInstanceImpl<T extends RuleUnit> implements RuleUnitInstance<T> {

    private static final String DEFAULT_RULE_UNIT = "DEFAULT_RULE_UNIT";

    private final RuleUnitDummyWorkingMemory dummyWorkingMemory;
    private final ConcurrentNodeMemories nodeMemories;
    private T unit;
    private final Kie.Runtime runtime;
    private InternalAgenda agenda;
    private final EntryPoints entryPoints;

    public RuleUnitInstanceImpl(T unit, Kie.Runtime runtime) {
        InternalKnowledgeBase kBase = runtime.kieBase();
        this.unit = unit;
        this.runtime = runtime;
        this.dummyWorkingMemory = new RuleUnitDummyWorkingMemory(this);
        this.agenda = new PatchedDefaultAgenda(kBase);
        this.nodeMemories = new ConcurrentNodeMemories(kBase, DEFAULT_RULE_UNIT);
        this.entryPoints = new EntryPoints(kBase, dummyWorkingMemory);
        this.agenda.setWorkingMemory(dummyWorkingMemory);
    }

    private int fireAllRules() {
        return fireAllRules(null, -1);
    }

    private int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        return this.agenda.fireAllRules(agendaFilter, fireLimit);
    }

    @Override
    public void run() {
        bindDataSources();
        fireAllRules();
    }

    private void bindDataSources() {
        try {
            for (Field field : unit.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object v = field.get(unit);
                if (field.getType().isAssignableFrom(DataSource.class)) {
                    DataSourceImpl<?> ds = (DataSourceImpl<?>) v;
                    ds.bind(this);
                } else {
                    field.set(unit, v);
                }
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException();
        }
    }

    InternalKnowledgeBase getInternalKnowledgeBase() {
        return runtime.kieBase();
    }

    EntryPoints getEntryPoints() {
        return this.entryPoints;
    }

    NodeMemories getNodeMemories() {
        return nodeMemories;
    }

    InternalAgenda getAgenda() {
        return agenda;
    }

    RuleUnitDummyWorkingMemory getWorkingMemory() {
        return dummyWorkingMemory;
    }

    @Override
    public T unit() {
        return unit;
    }
}

/**
 * Implements a collection of EntryPoints
 */
class EntryPoints {

    private InternalKnowledgeBase kBase;
    private final NamedEntryPoint ep;
    private final EntryPointNode epn;
    private final Map<String, WorkingMemoryEntryPoint> entryPoints = new ConcurrentHashMap<>();

    EntryPoints(InternalKnowledgeBase kBase, InternalWorkingMemoryActions dummyWorkingMemory) {
        this.kBase = kBase;
        this.epn = this.kBase.getRete().getEntryPointNode(EntryPointId.DEFAULT);
        this.ep = new NamedEntryPoint(EntryPointId.DEFAULT, epn, dummyWorkingMemory);
        if (kBase.getAddedEntryNodeCache() != null) {
            for (EntryPointNode addedNode : kBase.getAddedEntryNodeCache()) {
                EntryPointId id = addedNode.getEntryPoint();
                if (EntryPointId.DEFAULT.equals(id)) {
                    continue;
                }
                WorkingMemoryEntryPoint wmEntryPoint = new NamedEntryPoint(id, addedNode, dummyWorkingMemory);
                entryPoints.put(id.getEntryPointId(), wmEntryPoint);
            }
        }
    }

    WorkingMemoryEntryPoint get(String id) {
        if (EntryPointId.DEFAULT.getEntryPointId().equals(id)) {
            return defaultEntryPoint();
        }
        return entryPoints.get(id);
    }

    WorkingMemoryEntryPoint defaultEntryPoint() {
        return ep;
    }

    EntryPointNode defaultEntryPointNode() {
        return epn;
    }
}

/**
 * A severely limited implementation of the WorkingMemory interface
 * which delegates to the RuleUnitInstance. It only exists for legacy reasons.
 */
class RuleUnitDummyWorkingMemory implements InternalWorkingMemoryActions {

    private final RuleUnitInstanceImpl delegate;
    private SessionConfigurationImpl sessionConfiguration = new SessionConfigurationImpl();
    private SessionClock timerService = new JDKTimerService();
    private InternalKnowledgeRuntime dummyRuntime;

    RuleUnitDummyWorkingMemory(RuleUnitInstanceImpl delegate) {
        this.delegate = delegate;
    }

    @Override
    public InternalAgenda getAgenda() {
        return delegate.getAgenda();
    }

    @Override
    public PropagationList getPropagationList() {
        return delegate.getAgenda().getPropagationList();
    }

    @Override
    public InternalKnowledgeBase getKnowledgeBase() {
        return delegate.getInternalKnowledgeBase();
    }

    @Override
    public void setGlobal(String identifier, Object value) {
        reflectiveAccess(identifier).ifPresent(f -> write(f, value));
    }

    @Override
    public Object getGlobal(String identifier) {
        return reflectiveAccess(identifier).map(this::read).orElse(null);
    }

    private Optional<Field> reflectiveAccess(String identifier) {
        try {
            RuleUnit unit = delegate.unit();
            Field f = unit.getClass().getDeclaredField(identifier);
            f.setAccessible(true);
            return Optional.of(f);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }


    private Object read(Field f) {
        try {
            return f.get(delegate.unit());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void write(Field f, Object o) {
        try {
            f.set(delegate.unit(), o);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public Environment getEnvironment() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setGlobalResolver(GlobalResolver globalResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GlobalResolver getGlobalResolver() {
        return new MapGlobalResolver();
    }

    @Override
    public void delete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(FactHandle handle, Object object, BitMask mask, Class<?> modifiedClass, Activation activation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandle insert(Object object, boolean dynamic, RuleImpl rule, TerminalNode terminalNode) {
        NamedEntryPoint ep = (NamedEntryPoint) delegate.getEntryPoints().defaultEntryPoint();
        return ep.insert(object,
                  dynamic,
                  rule,
                  terminalNode);
    }

    @Override
    public FactHandle insertAsync(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTraits(InternalFactHandle h, BitMask mask, Class<?> modifiedClass, Activation activation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, K, X extends TraitableBean> Thing<K> shed(Activation activation, TraitableBean<K, X> core, Class<T> trait) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, K> T don(Activation activation, K core, Collection<Class<? extends Thing>> traits, boolean b, Mode[] modes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T, K> T don(Activation activation, K core, Class<T> trait, boolean b, Mode[] modes) {
        return null;
    }

    @Override
    public TruthMaintenanceSystem getTruthMaintenanceSystem() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fireAllRules() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fireAllRules(int fireLimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(FactHandle handle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends Object> getObjects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends Object> getObjects(ObjectFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getFactCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getEntryPointId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandle insert(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void retract(FactHandle handle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(FactHandle handle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(FactHandle handle, FactHandle.State fhState) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(FactHandle handle, Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(FactHandle handle, Object object, String... modifiedProperties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandle getFactHandle(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getIdentifier() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setIdentifier(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRuleRuntimeEventSupport(RuleRuntimeEventSupport workingMemoryEventSupport) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAgendaEventSupport(AgendaEventSupport agendaEventSupport) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
        return getNodeMemories().getNodeMemory(node, this);
    }

    @Override
    public void clearNodeMemory(MemoryFactory node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeMemories getNodeMemories() {
        return delegate.getNodeMemories();
    }

    long propagationIdCounter;

    @Override
    public long getNextPropagationIdCounter() {
        return propagationIdCounter++;
    }

    @Override
    public ObjectStore getObjectStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandleFactory getHandleFactory() {
        return delegate.getEntryPoints().defaultEntryPoint().getHandleFactory();
    }

    @Override
    public void queueWorkingMemoryAction(WorkingMemoryAction action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandleFactory getFactHandleFactory() {
        return new ReteooFactHandleFactory();
    }

    @Override
    public EntryPointId getEntryPoint() {
        return delegate.getEntryPoints().defaultEntryPoint().getEntryPoint();
    }

    @Override
    public InternalWorkingMemory getInternalWorkingMemory() {
        return this;
    }

    @Override
    public EntryPointNode getEntryPointNode() {
        return delegate.getEntryPoints().defaultEntryPointNode();
    }

    @Override
    public EntryPoint getEntryPoint(String name) {
        return delegate.getEntryPoints().get(name);
    }

    @Override
    public FactHandle getFactHandleByIdentity(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<?> iterateObjects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<?> iterateObjects(ObjectFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles(ObjectFilter filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFocus(String focus) {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResults getQueryResults(String query, Object... arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsyncExceptionHandler(AsyncExceptionHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearAgenda() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearAgendaGroup(String group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearActivationGroup(String group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearRuleFlowGroup(String group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessInstance startProcess(String processId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessInstance getProcessInstance(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessInstance getProcessInstance(long id, boolean readOnly) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkItemManager getWorkItemManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void halt() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactHandle insert(Object object, boolean dynamic) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkingMemoryEntryPoint getWorkingMemoryEntryPoint(String id) {
        return (WorkingMemoryEntryPoint) getEntryPoint(id);
    }

    @Override
    public void dispose() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SessionClock getSessionClock() {
//        throw new UnsupportedOperationException();
        return this.timerService;
    }

    @Override
    public Lock getLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSequential() {
        return true;
    }

    @Override
    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return delegate.getEntryPoints().defaultEntryPoint().getObjectTypeConfigurationRegistry();
    }

    @Override
    public InternalFactHandle getInitialFactHandle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Calendars getCalendars() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimerService getTimerService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalKnowledgeRuntime getKnowledgeRuntime() {
        return dummyRuntime;
    }

    @Override
    public Map<String, Channel> getChannels() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends EntryPoint> getEntryPoints() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SessionConfiguration getSessionConfiguration() {
        return sessionConfiguration;
    }

    @Override
    public void startBatchExecution() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void endBatchExecution() {

        throw new UnsupportedOperationException();
    }

    @Override
    public void startOperation() {
        // System.out.println("START OPERATION");
    }

    @Override
    public void endOperation() {
        // System.out.println("END OPERATION");
    }

    @Override
    public long getIdleTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTimeToNextJob() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateEntryPointsCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void prepareToFireActivation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void activationFired() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTotalFactCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalProcessRuntime getProcessRuntime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalProcessRuntime internalGetProcessRuntime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void closeLiveQuery(InternalFactHandle factHandle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPropagation(PropagationEntry propagationEntry) {
        delegate.getAgenda().addPropagation(propagationEntry);
    }

    @Override
    public void flushPropagations() {
        delegate.getAgenda().flushPropagations();
    }

    @Override
    public void activate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deactivate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryDeactivate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<? extends PropagationEntry> getActionsIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeGlobal(String identifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void notifyWaitOnRest() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelActivation(Activation activation, boolean declarativeAgenda) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEventListener(RuleRuntimeEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEventListener(RuleRuntimeEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return new AgendaEventSupport();
    }

    @Override
    public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
        return new RuleRuntimeEventSupport();
    }

    @Override
    public RuleEventListenerSupport getRuleEventSupport() {
        return new RuleEventListenerSupport();
    }

    @Override
    public void addEventListener(AgendaEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEventListener(AgendaEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<AgendaEventListener> getAgendaEventListeners() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEventListener(KieBaseEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEventListener(KieBaseEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<KieBaseEventListener> getKieBaseEventListeners() {
        throw new UnsupportedOperationException();
    }
}