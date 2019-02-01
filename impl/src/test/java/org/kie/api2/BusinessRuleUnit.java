package org.kie.api2;

import org.kie.api2.api.DataSource;

public class BusinessRuleUnit implements org.kie.api2.api.RuleUnit,
                                         org.kie.api.runtime.rule.RuleUnit {

    private DataSource<String> strings;

    public BusinessRuleUnit(DataSource<String> strings) {
        this.strings = strings;
    }
}
