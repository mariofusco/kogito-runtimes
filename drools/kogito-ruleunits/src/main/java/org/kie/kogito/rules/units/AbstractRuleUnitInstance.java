/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.rules.units;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.kie.api.runtime.rule.UnitRuntime;
import org.kie.api.time.SessionClock;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.RuleUnit;
import org.kie.kogito.rules.RuleUnitData;
import org.kie.kogito.rules.RuleUnitInstance;

public class AbstractRuleUnitInstance<T extends RuleUnitData> implements RuleUnitInstance<T> {

    private final T unitMemory;
    private final RuleUnit<T> unit;
    private final UnitRuntime runtime;

    public AbstractRuleUnitInstance( RuleUnit<T> unit, T unitMemory, UnitRuntime runtime ) {
        this.unit = unit;
        this.runtime = runtime;
        this.unitMemory = unitMemory;
        bind( runtime, unitMemory );
    }

    public int fire() {
        return runtime.fireAllRules();
    }

    public List<Map<String, Object>> executeQuery( String query, Object... arguments) {
        fire();
        return runtime.getQueryResults(query, arguments).toList();
    }

    @Override
    public RuleUnit<T> unit() {
        return unit;
    }

    @Override
    public <C extends SessionClock> C getClock() {
        return runtime.getSessionClock();
    }

    public T workingMemory() {
        return unitMemory;
    }

    protected void bind(UnitRuntime runtime, T unitMemory) {
        try {
            for (Field f : unitMemory.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object value = f.get(unitMemory);
                runtime.bindUnitField( value, f.getName(), f.getName(), value instanceof DataSource );
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }
}
