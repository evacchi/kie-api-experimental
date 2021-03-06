package org.kie.api2.api;

import org.drools.core.impl.InternalKnowledgeBase;

public interface Kie {

    interface Runtime {

        InternalKnowledgeBase kieBase();

        Factory factory();

        interface Factory {

            // type-safe API: we add one case for each type of unit we support
            // the base case of(U), with <U extends Unit>
            // is the fallback case.
            // Unit u = new MyUnit() // with MyUnit implementing RuleUnit
            // factory.of(u) may delegate to the other known overloads and/or throw

            <U extends Unit> UnitInstance<U> of(U unit);

            <U extends RuleUnit> RuleUnitInstance<U> of(U unit);

            <U extends ProcessUnit> ProcessUnitInstance<U> of(U unit);

            <U extends BayesUnit> BayesUnitInstance<U> of(U Unit);
        }

        interface Provider {

            Kie.Runtime runtime();
        }
    }
}
