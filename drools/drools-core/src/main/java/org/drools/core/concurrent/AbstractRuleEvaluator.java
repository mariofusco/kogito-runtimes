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

package org.drools.core.concurrent;

import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.InternalAgenda;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.spi.KnowledgeHelper;
import org.kie.api.runtime.rule.AgendaFilter;

public class AbstractRuleEvaluator {
    private final InternalAgenda agenda;

    public AbstractRuleEvaluator( InternalAgenda agenda ) {
        this.agenda = agenda;
    }

    protected int internalEvaluateAndFire( AgendaFilter filter, int fireCount, int fireLimit, RuleAgendaItem item ) {
        agenda.evaluateQueriesForRule( item );
        return item.getRuleExecutor().evaluateNetworkAndFire(agenda, filter, fireCount, fireLimit);
    }

    protected KnowledgeHelper newKnowledgeHelper() {
        RuleBaseConfiguration rbc = agenda.getWorkingMemory().getKnowledgeBase().getConfiguration();
        return rbc.getComponentFactory().getKnowledgeHelperFactory().newStatefulKnowledgeHelper( agenda.getWorkingMemory() );
    }
}
