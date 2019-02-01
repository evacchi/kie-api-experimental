package org.kie.api2.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.drools.core.RuleBaseConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.ClassAwareObjectStore;
import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.datasources.InternalDataSource;
import org.drools.core.factmodel.traits.TraitTypeEnum;
import org.drools.core.phreak.PropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.ReteooFactHandleFactory;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.spi.Activation;
import org.drools.core.spi.PropagationContext;
import org.drools.core.spi.Tuple;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.RuleUnit;
import org.kie.api2.api.DataSource;

import static org.drools.core.common.DefaultFactHandle.determineIdentityHashCode;
import static org.drools.core.ruleunit.RuleUnitUtil.RULE_UNIT_ENTRY_POINT;

public class DataSourceImpl<T> implements DataSource<T> {

    private static final ReteooFactHandleFactory FACT_HANDLE_FACTORY = new ReteooFactHandleFactory();

    private ObjectStore objectStore = new ClassAwareObjectStore(RuleBaseConfiguration.AssertBehaviour.IDENTITY, null);

    private List<T> inserted = new ArrayList<>();

    @Override
    public FactHandle add(T object) {
        inserted.add(object);
        return null;
    }

    void bind(RuleUnitInstanceImpl ruleUnitInstance) {
        InternalAgenda agenda = ruleUnitInstance.getAgenda();

        String unitName = ruleUnitInstance.unit().getClass().getCanonicalName();
        EntryPoints entryPoints = ruleUnitInstance.getEntryPoints();
        WorkingMemoryEntryPoint workingMemoryEntryPoint = entryPoints.get(RULE_UNIT_ENTRY_POINT);
        workingMemoryEntryPoint.insert(ruleUnitInstance.unit());

        InternalAgendaGroup agendaGroup =
                (InternalAgendaGroup) agenda.getAgendaGroup(unitName);
        agendaGroup.setAutoDeactivate(false);
        agendaGroup.setFocus();

        inserted.forEach(object -> {
            DataSourceFactHandle factHandle = new DataSourceFactHandle(
                    this, FACT_HANDLE_FACTORY.getNextId(), FACT_HANDLE_FACTORY.getNextRecency(), object);
            objectStore.addHandle(factHandle, object);
            ruleUnitInstance.getAgenda().getPropagationList().addEntry(new Insert(factHandle, ruleUnitInstance));
        });

        inserted = null;
    }

    @Override
    public void update(FactHandle handle, T object) {
    }

    @Override
    public void remove(FactHandle handle) {
//        DataSourceFactHandle dsFh = (DataSourceFactHandle) fh;
//        objectStore.removeHandle(dsFh);
////        propagate(workingMemory, () -> new Delete(dsFh, null));
//        throw new UnsupportedOperationException();

    }

    public void remove(Object obj) {
        remove(objectStore.getHandleForObject(obj));
    }

    private void propagate(RuleUnitInstanceImpl ruleUnitInstance, AbstractDataSourcePropagation s) {
        // FIXME I am not sure why this is working... if I use RULE_UNIT_ENTRY_POINT it breaks...
        // `workingMemory` is equivalent to:
        // EntryPoint entryPoint = workingMemory.getEntryPoint(
        //         EntryPointId.DEFAULT.getEntryPointId());
        PropagationList propagationList = ruleUnitInstance.getAgenda().getPropagationList();
        RuleUnitDummyWorkingMemory workingMemory = ruleUnitInstance.getWorkingMemory();
        AbstractDataSourcePropagation propagationEntry = s.setEntryPoint(workingMemory);
        propagationList.addEntry(s);
    }

    private void flush(EntryPoint ep, PropagationEntry currentHead) {
        for (PropagationEntry entry = currentHead; entry != null; entry = entry.getNext()) {
            ((AbstractDataSourcePropagation) entry).execute(ep);
        }
    }

    public void unbind(RuleUnit unit) {
    }

    public Iterator<T> iterator() {
        return inserted != null ? inserted.iterator() : (Iterator<T>) objectStore.iterateObjects();
    }

    static abstract class AbstractDataSourcePropagation extends PropagationEntry.AbstractPropagationEntry {

        private EntryPoint ep;

        public AbstractDataSourcePropagation setEntryPoint(EntryPoint ep) {
            this.ep = ep;
            return this;
        }

        @Override
        public void execute(InternalWorkingMemory wm) {
            execute(ep);
        }

        public abstract void execute(EntryPoint ep);
    }

    static class Insert extends AbstractDataSourcePropagation {

        private final DataSourceFactHandle dsFactHandle;
        private final RuleUnitInstanceImpl ruleUnitInstance;

        public Insert(DataSourceFactHandle factHandle, RuleUnitInstanceImpl ruleUnitInstance) {
            this.dsFactHandle = factHandle;
            this.ruleUnitInstance = ruleUnitInstance;
            // FIXME I am not sure why this is working... if I use RULE_UNIT_ENTRY_POINT it breaks...
            //  the returned `DummyWorkingMemory` instance delegates to:
            //  EntryPoint entryPoint = workingMemory.getEntryPoint(
            //         EntryPointId.DEFAULT.getEntryPointId());
            setEntryPoint(ruleUnitInstance.getWorkingMemory());
        }

        @Override
        public void execute(EntryPoint entryPoint) {
            WorkingMemoryEntryPoint ep = (WorkingMemoryEntryPoint) entryPoint;
            ObjectTypeConf typeConf = ep.getObjectTypeConfigurationRegistry()
                    .getObjectTypeConf(ep.getEntryPoint(), dsFactHandle.getObject());

            InternalFactHandle handleForEp = dsFactHandle.createFactHandleFor(ep, typeConf);
//            RuleUnit ruleUnit = ep.getInternalWorkingMemory().getRuleUnitExecutor().getCurrentRuleUnit();
//            dsFactHandle.childHandles.put(ruleUnit.getUnitIdentity(), handleForEp);

            PropagationContextFactory pctxFactory =
                    ep.getKnowledgeBase().getConfiguration().getComponentFactory().getPropagationContextFactory();

            PropagationContext context = pctxFactory.createPropagationContext(ep.getInternalWorkingMemory().getNextPropagationIdCounter(),
                                                                              PropagationContext.Type.INSERTION,
                                                                              null,
                                                                              null,
                                                                              handleForEp,
                                                                              ep.getEntryPoint());
            for (ObjectTypeNode otn : typeConf.getObjectTypeNodes()) {
                otn.propagateAssert(handleForEp, context, ep.getInternalWorkingMemory());
            }
        }
    }

    static class Update extends AbstractDataSourcePropagation {

        private final DataSourceFactHandle dsFactHandle;
        private final Object object;
        private final BitMask mask;
        private final Class<?> modifiedClass;
        private final Activation activation;

        Update(DataSourceFactHandle factHandle, Object object, BitMask mask, Class<?> modifiedClass, Activation activation) {
            this.dsFactHandle = factHandle;
            this.object = object;
            this.mask = mask;
            this.modifiedClass = modifiedClass;
            this.activation = activation;
        }

        @Override
        public void execute(EntryPoint entryPoint) {
            WorkingMemoryEntryPoint ep = (WorkingMemoryEntryPoint) entryPoint;
            ObjectTypeConf typeConf = ep.getObjectTypeConfigurationRegistry()
                    .getObjectTypeConf(ep.getEntryPoint(), object);

            RuleUnit ruleUnit = ep.getInternalWorkingMemory().getRuleUnitExecutor().getCurrentRuleUnit();
            InternalFactHandle handle = dsFactHandle.childHandles.get(ruleUnit.getUnitIdentity());

            PropagationContextFactory pctxFactory = ((InternalWorkingMemoryEntryPoint) ep).getPctxFactory();
            PropagationContext context = pctxFactory.createPropagationContext(ep.getInternalWorkingMemory().getNextPropagationIdCounter(),
                                                                              PropagationContext.Type.MODIFICATION,
                                                                              activation == null ? null : activation.getRule(),
                                                                              activation == null ? null : activation.getTuple().getTupleSink(),
                                                                              handle,
                                                                              ep.getEntryPoint(),
                                                                              mask,
                                                                              modifiedClass,
                                                                              null);

            EntryPointNode.propagateModify(handle, context, typeConf, ep.getInternalWorkingMemory());
        }
    }

    static class Delete extends AbstractDataSourcePropagation {

        private final DataSourceFactHandle dsFactHandle;
        private final Activation activation;

        Delete(DataSourceFactHandle factHandle, Activation activation) {
            this.dsFactHandle = factHandle;
            this.activation = activation;
        }

        @Override
        public void execute(EntryPoint entryPoint) {
            WorkingMemoryEntryPoint ep = (WorkingMemoryEntryPoint) entryPoint;
            ObjectTypeConf typeConf = ep.getObjectTypeConfigurationRegistry()
                    .getObjectTypeConf(ep.getEntryPoint(), dsFactHandle.getObject());

            RuleUnit ruleUnit = ep.getInternalWorkingMemory().getRuleUnitExecutor().getCurrentRuleUnit();
            InternalFactHandle handle = dsFactHandle.childHandles.get(ruleUnit.getUnitIdentity());

            PropagationContextFactory pctxFactory = ((InternalWorkingMemoryEntryPoint) ep).getPctxFactory();
            PropagationContext context = pctxFactory.createPropagationContext(ep.getInternalWorkingMemory().getNextPropagationIdCounter(),
                                                                              PropagationContext.Type.DELETION,
                                                                              activation == null ? null : activation.getRule(),
                                                                              activation == null ? null : activation.getTuple().getTupleSink(),
                                                                              handle,
                                                                              ep.getEntryPoint());

            ep.getEntryPointNode().propagateRetract(handle, context, typeConf, ep.getInternalWorkingMemory());
        }
    }

    public static class DataSourceFactHandle implements InternalFactHandle {

        private final DataSourceImpl<?> dataSource;
        private Object object;

        private final Map<RuleUnit.Identity, InternalFactHandle> childHandles = new HashMap<>();

        private final int id;
        private long recency;

        private final int identityHashCode;

        private boolean negated = false;

        DataSourceFactHandle(DataSourceImpl<?> dataSource, int id, long recency, Object object) {
            this.dataSource = dataSource;
            this.id = id;
            this.recency = recency;
            this.object = object;
            identityHashCode = determineIdentityHashCode(object);
        }

        InternalFactHandle createFactHandleFor(WorkingMemoryEntryPoint ep, ObjectTypeConf conf) {
            InternalFactHandle fh = ep.getHandleFactory().newFactHandle(id, object, recency, conf, ep.getInternalWorkingMemory(), ep);
            fh.setNegated(negated);
            fh.setParentHandle(this);
            return fh;
        }

        @Override
        public InternalDataSource<?> getDataSource() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public Object getObject() {
            return object;
        }

        @Override
        public boolean isNegated() {
            return negated;
        }

        @Override
        public void setNegated(boolean negated) {
            this.negated = negated;
        }

        @Override
        public int getIdentityHashCode() {
            return identityHashCode;
        }

        @Override
        public long getRecency() {
            return recency;
        }

        @Override
        public void setRecency(long recency) {
            this.recency = recency;
        }

        @Override
        public String getObjectClassName() {
            return object.getClass().getName();
        }

        @Override
        public void setObject(Object object) {
            this.object = object;
        }

        @Override
        public void setEqualityKey(EqualityKey key) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.setEqualityKey -> TODO");
        }

        @Override
        public EqualityKey getEqualityKey() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getEqualityKey -> TODO");
        }

        @Override
        public void invalidate() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.invalidate -> TODO");
        }

        @Override
        public boolean isValid() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isValid -> TODO");
        }

        @Override
        public int getObjectHashCode() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getObjectHashCode -> TODO");
        }

        @Override
        public boolean isDisconnected() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isDisconnected -> TODO");
        }

        @Override
        public boolean isEvent() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isEvent -> TODO");
        }

        @Override
        public boolean isTraitOrTraitable() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isTraitOrTraitable -> TODO");
        }

        @Override
        public boolean isTraitable() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isTraitable -> TODO");
        }

        @Override
        public boolean isTraiting() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isTraiting -> TODO");
        }

        @Override
        public TraitTypeEnum getTraitType() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getTraitType -> TODO");
        }

        @Override
        public RightTuple getFirstRightTuple() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getFirstRightTuple -> TODO");
        }

        @Override
        public LeftTuple getFirstLeftTuple() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getFirstLeftTuple -> TODO");
        }

        @Override
        public WorkingMemoryEntryPoint getEntryPoint() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getEntryPoint -> TODO");
        }

        @Override
        public void setEntryPoint(WorkingMemoryEntryPoint ep) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.setEntryPoint -> TODO");
        }

        @Override
        public InternalFactHandle clone() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.clone -> TODO");
        }

        @Override
        public String toExternalForm() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.toExternalForm -> TODO");
        }

        @Override
        public void disconnect() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.disconnect -> TODO");
        }

        @Override
        public void addFirstLeftTuple(LeftTuple leftTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.addFirstLeftTuple -> TODO");
        }

        @Override
        public void addLastLeftTuple(LeftTuple leftTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.addLastLeftTuple -> TODO");
        }

        @Override
        public void removeLeftTuple(LeftTuple leftTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.removeLeftTuple -> TODO");
        }

        @Override
        public void clearLeftTuples() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.clearLeftTuples -> TODO");
        }

        @Override
        public void clearRightTuples() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.clearRightTuples -> TODO");
        }

        @Override
        public void addFirstRightTuple(RightTuple rightTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.addFirstRightTuple -> TODO");
        }

        @Override
        public void addLastRightTuple(RightTuple rightTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.addLastRightTuple -> TODO");
        }

        @Override
        public void removeRightTuple(RightTuple rightTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.removeRightTuple -> TODO");
        }

        @Override
        public void addTupleInPosition(Tuple tuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.addTupleInPosition -> TODO");
        }

        @Override
        public <K> K as(Class<K> klass) throws ClassCastException {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.as -> TODO");
        }

        @Override
        public boolean isExpired() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isExpired -> TODO");
        }

        @Override
        public boolean isPendingRemoveFromStore() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.isPendingRemoveFromStore -> TODO");
        }

        @Override
        public void forEachRightTuple(Consumer<RightTuple> rightTupleConsumer) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.forEachRightTuple -> TODO");
        }

        @Override
        public void forEachLeftTuple(Consumer<LeftTuple> leftTupleConsumer) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.forEachLeftTuple -> TODO");
        }

        @Override
        public RightTuple findFirstRightTuple(Predicate<RightTuple> rightTuplePredicate) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.findFirstRightTuple -> TODO");
        }

        @Override
        public LeftTuple findFirstLeftTuple(Predicate<LeftTuple> lefttTuplePredicate) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.findFirstLeftTuple -> TODO");
        }

        @Override
        public void setFirstLeftTuple(LeftTuple firstLeftTuple) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.setFirstLeftTuple -> TODO");
        }

        @Override
        public LinkedTuples detachLinkedTuples() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.detachLinkedTuples -> TODO");
        }

        @Override
        public LinkedTuples detachLinkedTuplesForPartition(int i) {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.detachLinkedTuplesForPartition -> TODO");
        }

        @Override
        public LinkedTuples getLinkedTuples() {
            throw new UnsupportedOperationException("org.drools.core.datasources.DataSourceFactHandle.getLinkedTuples -> TODO");
        }
    }
}
