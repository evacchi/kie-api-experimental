package org.kie.api2.impl;

import java.util.NoSuchElementException;

import org.kie.api.KieBase;
import org.kie.api2.api.BayesUnit;
import org.kie.api2.api.BayesUnitInstance;
import org.kie.api2.api.BayesUnitInstanceFactory;
import org.kie.api2.api.Kie;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstance;
import org.kie.api2.api.ProcessUnitInstanceFactory;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.api.RuleUnitInstance;
import org.kie.api2.api.RuleUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;
import org.kie.api2.api.UnitInstanceFactory;

public class KieRuntimeFactoryImpl implements Kie.Runtime.Factory {

    private final KieBase kBase;

    public KieRuntimeFactoryImpl(KieBase kBase) {
        this.kBase = kBase;
    }

    @Override
    public <U extends Unit> UnitInstance<U> of(U unit) {
        UnitInstanceFactory factory = lookup(unit.getClass());
        return factory.create(unit);
    }

    @Override
    public <U extends RuleUnit> RuleUnitInstance<U> of(U unit) {
        RuleUnitInstanceFactory factory = lookupFactory(RuleUnitInstanceFactory.class);
        return (RuleUnitInstance<U>) factory.create(unit);
    }

    @Override
    public <U extends ProcessUnit> ProcessUnitInstance<U> of(U unit) {
        ProcessUnitInstanceFactory factory = lookupFactory(ProcessUnitInstanceFactory.class);
        return (ProcessUnitInstance<U>) factory.create(unit);
    }

    public <U extends BayesUnit> BayesUnitInstance<U> of(U unit) {
        BayesUnitInstanceFactory factory = lookupFactory(BayesUnitInstanceFactory.class);
        return (BayesUnitInstance<U>) factory.create(unit);
    }

    private UnitInstanceFactory lookup(Class<? extends Unit> unitClass) {
        if (unitClass == RuleUnit.class) {
            return lookupFactory(RuleUnitInstanceFactory.class);
        } else if (unitClass == ProcessUnit.class) {
            return lookupFactory(ProcessUnitInstanceFactory.class);
        } else if (unitClass == BayesUnit.class) {
            return lookupFactory(BayesUnitInstanceFactory.class);
        }
        throw new NoSuchElementException();
    }

    private <T extends UnitInstanceFactory> T lookupFactory(Class<T> cls) {
        // wizardry left as exercise for the reader, for now we are doing it manually
        // this could be either auto-generated code (known statically) or runtime-based (best for testing)
        if (cls == RuleUnitInstanceFactory.class) {
            return (T) new RuleUnitInstanceFactoryImpl(kBase);
        } else if (cls == ProcessUnitInstanceFactory.class) {
            return (T) new ProcessUnitInstanceFactoryImpl(kBase);
        } else if (cls == BayesUnitInstanceFactory.class) {
            return (T) new BayesUnitInstanceFactoryImpl(kBase);
        } else {
            throw new NoSuchElementException();
        }
    }
}
