/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.rules.units.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.kie.kogito.Application;
import org.kie.kogito.rules.RuleUnitData;

import static java.util.stream.Collectors.toList;

public class UnitScheduler {

    private final Application application;

    private final List<RuleUnitData[]> scheduledUnits = new ArrayList<>();

    public UnitScheduler( Application application ) {
        this.application = application;
    }

    public UnitWorkflow sequential( RuleUnitData unit ) {
        return new UnitWorkflow.Single( application.ruleUnits().create((Class<RuleUnitData>) unit.getClass()).createInstance( unit ) );
    }

    public UnitWorkflow sequential( UnitWorkflow... units ) {
        return new UnitWorkflow.Multiple( Arrays.asList( units ), false );
    }

    public UnitWorkflow sequential( RuleUnitData unit, RuleUnitData... units ) {
        return sequential( unit, unit2workflow( units ) );
    }

    public UnitWorkflow sequential( RuleUnitData unit, UnitWorkflow... units ) {
        return multipleWorkflow( unit, units, false );
    }

    public UnitWorkflow parallel( UnitWorkflow... units ) {
        return new UnitWorkflow.Multiple( Arrays.asList( units ), true );
    }

    public UnitWorkflow parallel( RuleUnitData unit, RuleUnitData... units ) {
        return parallel( unit, unit2workflow( units ) );
    }

    public UnitWorkflow parallel( RuleUnitData unit, UnitWorkflow... units ) {
        return multipleWorkflow( unit, units, true );
    }

    private UnitWorkflow createWorkflow( RuleUnitData[] units, boolean parallel ) {
        return new UnitWorkflow.Multiple( toWorkflowList( units ), parallel );
    }

    private List<UnitWorkflow> toWorkflowList( RuleUnitData[] units ) {
        return Stream.of( units ).map( this::sequential ).collect( toList() );
    }

    private UnitWorkflow[] unit2workflow( RuleUnitData[] units ) {
        UnitWorkflow[] workflows = new UnitWorkflow[units.length];
        for (int i = 0; i < units.length; i++) {
            workflows[i] = sequential( units[i] );
        }
        return workflows;
    }

    private UnitWorkflow multipleWorkflow( RuleUnitData unit, UnitWorkflow[] units, boolean parallel ) {
        List<UnitWorkflow> workflows = new ArrayList<>();
        workflows.add( sequential( unit ) );
        workflows.addAll( Arrays.asList( units ) );
        return new UnitWorkflow.Multiple( workflows, parallel );
    }
}
