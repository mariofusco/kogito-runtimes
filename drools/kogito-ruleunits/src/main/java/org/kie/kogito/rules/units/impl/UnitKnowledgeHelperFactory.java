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

package org.kie.kogito.rules.units.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.drools.core.WorkingMemory;
import org.drools.core.base.KnowledgeHelperFactory;
import org.drools.core.base.SequentialKnowledgeHelper;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.factmodel.traits.Thing;
import org.drools.core.factmodel.traits.TraitableBean;
import org.drools.core.rule.Declaration;
import org.drools.core.spi.Activation;
import org.drools.core.spi.KnowledgeHelper;
import org.drools.core.spi.Tuple;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.internal.runtime.beliefs.Mode;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;

public class UnitKnowledgeHelperFactory implements KnowledgeHelperFactory, Serializable {

    @Override
    public KnowledgeHelper newSequentialKnowledgeHelper(WorkingMemory wm) {
        return new SequentialKnowledgeHelper( wm );
    }

    @Override
    public KnowledgeHelper newStatefulKnowledgeHelper(WorkingMemory wm) {
        return new UnitKnowledgeHelper( (InternalWorkingMemory) wm );
    }

    public class UnitKnowledgeHelper implements KnowledgeHelper {

        private final InternalWorkingMemory workingMemory;

        private Activation activation;
        private Tuple tuple;

        public UnitKnowledgeHelper( InternalWorkingMemory workingMemory ) {
            this.workingMemory = workingMemory;
        }

        @Override
        public void setActivation(final Activation agendaItem) {
            this.activation = agendaItem;
            agendaItem.setLogicalDependencies( null );
            agendaItem.setBlocked( null );
            this.tuple = agendaItem.getTuple();
        }

        @Override
        public void reset() {
            this.activation = null;
            this.tuple = null;
        }

        @Override
        public void cancelRemainingPreviousLogicalDependencies() {
        }

        @Override
        public Activation getMatch() {
            return this.activation;
        }

        @Override
        public Tuple getTuple() {
            return this.tuple;
        }

        @Override
        public void update( FactHandle handle, BitMask mask, Class<?> modifiedClass ) {
            InternalFactHandle h = (InternalFactHandle) handle;

            if (h.getDataStore() != null) {
                // This handle has been insert from a datasource, so update it
                h.getDataStore().update( h,
                        h.getObject(),
                        mask,
                        modifiedClass,
                        this.activation );
                return;
            }

            (( InternalWorkingMemoryEntryPoint ) h.getEntryPoint()).update( h,
                    ((InternalFactHandle)handle).getObject(),
                    mask,
                    modifiedClass,
                    this.activation );
        }

        @Override
        public void run(String ruleUnitName) {
            ( (UnitRuntimeImpl.WorkingMemoryAdapter) workingMemory).getApplication().ruleUnits().getRegisteredInstance( ruleUnitName ).fire();
        }

        @Override
        public InternalFactHandle insert( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insert -> TODO" );

        }

        @Override
        public FactHandle insertAsync( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertAsync -> TODO" );

        }

        @Override
        public InternalFactHandle insert( Object object, boolean dynamic ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insert -> TODO" );

        }

        @Override
        public InternalFactHandle insertLogical( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertLogical -> TODO" );

        }

        @Override
        public FactHandle insertLogical( Object object, Object value ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertLogical -> TODO" );

        }

        @Override
        public void blockMatch( Match match ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.blockMatch -> TODO" );

        }

        @Override
        public void unblockAllMatches( Match match ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.unblockAllMatches -> TODO" );

        }

        @Override
        public void cancelMatch( Match match ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.cancelMatch -> TODO" );

        }

        @Override
        public InternalFactHandle insertLogical( Object object, boolean dynamic ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertLogical -> TODO" );

        }

        @Override
        public InternalFactHandle insertLogical( Object object, Mode belief ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertLogical -> TODO" );

        }

        @Override
        public InternalFactHandle insertLogical( Object object, Mode... beliefs ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.insertLogical -> TODO" );

        }

        @Override
        public InternalFactHandle getFactHandle( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getFactHandle -> TODO" );

        }

        @Override
        public InternalFactHandle getFactHandle( InternalFactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getFactHandle -> TODO" );

        }

        @Override
        public void update( FactHandle handle, Object newObject ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.update -> TODO" );

        }

        @Override
        public void update( FactHandle newObject ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.update -> TODO" );

        }

        @Override
        public void update( Object newObject ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.update -> TODO" );

        }

        @Override
        public void update( Object newObject, BitMask mask, Class<?> modifiedClass ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.update -> TODO" );

        }

        @Override
        public void retract( FactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.retract -> TODO" );

        }

        @Override
        public void retract( Object handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.retract -> TODO" );

        }

        @Override
        public void delete( Object handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.delete -> TODO" );

        }

        @Override
        public void delete( Object object, FactHandle.State fhState ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.delete -> TODO" );

        }

        @Override
        public void delete( FactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.delete -> TODO" );

        }

        @Override
        public void delete( FactHandle handle, FactHandle.State fhState ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.delete -> TODO" );

        }

        @Override
        public Object get( Declaration declaration ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.get -> TODO" );

        }

        @Override
        public RuleImpl getRule() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getRule -> TODO" );

        }

        @Override
        public WorkingMemory getWorkingMemory() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getWorkingMemory -> TODO" );

        }

        @Override
        public EntryPoint getEntryPoint( String id ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getEntryPoint -> TODO" );

        }

        @Override
        public Channel getChannel( String id ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getChannel -> TODO" );

        }

        @Override
        public Map<String, Channel> getChannels() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getChannels -> TODO" );

        }

        @Override
        public void setFocus( String focus ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.setFocus -> TODO" );

        }

        @Override
        public Declaration getDeclaration( String identifier ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getDeclaration -> TODO" );

        }

        @Override
        public void halt() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.halt -> TODO" );

        }

        @Override
        public <T> T getContext( Class<T> contextClass ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getContext -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Class<T> trait, boolean logical ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Class<T> trait, Mode... modes ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Class<T> trait ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( Thing<K> core, Class<T> trait ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Collection<Class<? extends Thing>> trait, boolean logical ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Collection<Class<? extends Thing>> trait, Mode... modes ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> T don( K core, Collection<Class<? extends Thing>> trait ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.don -> TODO" );

        }

        @Override
        public <T, K> Thing<K> shed( Thing<K> thing, Class<T> trait ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.shed -> TODO" );

        }

        @Override
        public <T, K, X extends TraitableBean> Thing<K> shed( TraitableBean<K, X> core, Class<T> trait ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.shed -> TODO" );

        }

        @Override
        public InternalFactHandle bolster( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.bolster -> TODO" );

        }

        @Override
        public InternalFactHandle bolster( Object object, Object value ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.bolster -> TODO" );

        }

        @Override
        public ClassLoader getProjectClassLoader() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getProjectClassLoader -> TODO" );

        }

        @Override
        public KieRuntime getKieRuntime() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getKieRuntime -> TODO" );

        }

        @Override
        public KieRuntime getKnowledgeRuntime() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitKnowledgeHelperFactory.UnitKnowledgeHelper.getKnowledgeRuntime -> TODO" );

        }
    }
}
