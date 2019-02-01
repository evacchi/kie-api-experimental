package org.kie.api2;

import java.util.List;

import org.kie.api2.api.DataSource;

public class BusinessRuleUnit implements org.kie.api2.api.RuleUnit,
                                         org.kie.api.runtime.rule.RuleUnit {

    private DataSource<String> strings;
    private List<String> list;

    public BusinessRuleUnit() {
    }

    public BusinessRuleUnit(DataSource<String> strings) {
        this.strings = strings;
    }
}
