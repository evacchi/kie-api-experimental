/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.api2.impl;

import org.drools.core.base.DefaultKnowledgeHelper;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.concurrent.RuleEvaluator;
import org.drools.core.concurrent.SequentialRuleEvaluator;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.spi.KnowledgeHelper;
import org.kie.api.runtime.rule.AgendaFilter;

/**
 * A patched, simplified version of the original {@link SequentialRuleEvaluator}.
 */
public class PatchedSequentialRuleEvaluator implements RuleEvaluator {

    private final KnowledgeHelper knowledgeHelper;
    private final PatchedDefaultAgenda agenda;

    public PatchedSequentialRuleEvaluator(PatchedDefaultAgenda agenda) {
        this.agenda = agenda;
        knowledgeHelper = new DefaultKnowledgeHelper(agenda.getWorkingMemory());
    }

    @Override
    public int evaluateAndFire(AgendaFilter filter,
                               int fireCount,
                               int fireLimit,
                               InternalAgendaGroup group) {
        RuleAgendaItem item = (RuleAgendaItem) group.remove();
        if (item == null) {
            return 0;
        } else {
            agenda.evaluateQueriesForRule(item);
            return item.getRuleExecutor().evaluateNetworkAndFire(agenda, filter, fireCount, fireLimit);
        }
    }

    public KnowledgeHelper getKnowledgeHelper() {
        return knowledgeHelper;
    }
}
