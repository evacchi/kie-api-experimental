package org.kie.api2.api;

/**
 * An instance of a {@link Unit}, i.e. its runtime representation.
 *
 * It is similar to the Process/ProcessInstance pair that we have in jBPM.
 * This is a generalized concept: we have Unit and UnitInstance; both are only abstract:
 * concrete impls are for the subtypes: RuleUnit(Instance) and ProcessUnit(Instance)
 * (and etc...)
 */
public interface UnitInstance<T> {

    T unit();

    void run();
}
