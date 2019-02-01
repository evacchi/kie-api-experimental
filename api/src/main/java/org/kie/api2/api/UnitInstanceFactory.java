package org.kie.api2.api;

public interface UnitInstanceFactory {

    <U extends Unit> UnitInstance<U> create(U unit);
}
