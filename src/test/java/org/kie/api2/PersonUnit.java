package org.kie.api2;

import org.kie.api2.api.DataSource;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.model.Person;

public class PersonUnit implements org.kie.api2.api.RuleUnit,
                                   org.kie.api.runtime.rule.RuleUnit {

    private final DataSource<Person> persons;

    public PersonUnit(DataSource<Person> persons) {
        this.persons = persons;
    }
}
