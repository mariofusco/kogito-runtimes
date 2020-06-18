/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.drools.core.InitialFact;
import org.drools.core.QueryResultsImpl;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.base.DroolsQuery;
import org.drools.core.base.MapGlobalResolver;
import org.drools.core.base.NonCloningQueryViewListener;
import org.drools.core.base.QueryRowWithSubruleIndex;
import org.drools.core.common.BaseNode;
import org.drools.core.common.ConcurrentNodeMemories;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.phreak.PropagationEntry;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.SegmentUtilities;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.InitialFactImpl;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.Declaration;
import org.drools.core.rule.EntryPointId;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.spi.Activation;
import org.drools.core.spi.AsyncExceptionHandler;
import org.drools.core.spi.FactHandleFactory;
import org.drools.core.spi.GlobalResolver;
import org.drools.core.spi.PropagationContext;
import org.drools.core.time.TimerServiceFactory;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.KieBase;
import org.kie.api.event.kiebase.KieBaseEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.logger.KieRuntimeLogger;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.UnitRuntime;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.kogito.Application;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.RuleEventListenerConfig;
import org.kie.services.time.TimerService;

import static org.drools.core.base.ClassObjectType.InitialFact_ObjectType;
import static org.drools.core.impl.StatefulKnowledgeSessionImpl.DEFAULT_RULE_UNIT;

public class UnitRuntimeImpl implements UnitRuntime {

    private final InternalKnowledgeBase kBase;
    private final SessionConfiguration config;
    private final Application application;

    private final InternalWorkingMemory workingMemory;

    public UnitRuntimeImpl( Application application, InternalKnowledgeBase kBase, SessionConfiguration config ) {
        this.kBase = kBase;
        this.config = config;
        this.application = application;
        kBase.getConfiguration().getComponentFactory().setKnowledgeHelperFactory(new UnitKnowledgeHelperFactory());
        this.workingMemory = new WorkingMemoryAdapter( this, config );
    }

    @Override
    public void bindUnitField( Object field, String fieldName, String entryPointName, boolean isDataSource ) {
        if ( isDataSource ) {
            DataSource<?> o = ( DataSource<?> ) field;
            EntryPointNode epNode = kBase.getRete().getEntryPointNode( new EntryPointId( entryPointName ) );
            o.subscribe(new EntryPointNodeDataProcessor( epNode, workingMemory ));
        }
        try {
            workingMemory.setGlobal( fieldName, field );
        } catch (RuntimeException e) {
            // ignore if the global doesn't exist
        }
    }

    @Override
    public int fireAllRules() {
        return workingMemory.fireAllRules();
    }

    @Override
    public QueryResults getQueryResults( String query, Object... arguments ) {
        return workingMemory.getQueryResults( query, arguments );
    }

    @Override
    public <T extends SessionClock> T getSessionClock() {
        return (T) workingMemory.getSessionClock();
    }

    public static class WorkingMemoryAdapter implements InternalWorkingMemory, InternalKnowledgeRuntime {

        private final UnitRuntimeImpl runtime;

        private final AtomicLong propagationIdCounter = new AtomicLong();

        private final FactHandleFactory handleFactory;

        private final ReentrantLock lock = new ReentrantLock();

        private final RuleEventListenerSupport ruleEventListenerSupport = new RuleEventListenerSupport();
        private final RuleRuntimeEventSupport ruleRuntimeEventSupport = new RuleRuntimeEventSupport();
        private final AgendaEventSupport agendaEventSupport = new AgendaEventSupport();

        private final GlobalResolver globalResolver = new MapGlobalResolver();

        private final NodeMemories nodeMemories;

        private final InternalAgenda agenda;

        private final PropagationContextFactory pctxFactory;

        private final TimerService timerService;

        private WorkingMemoryAdapter( UnitRuntimeImpl runtime, SessionConfiguration config ) {
            this.runtime = runtime;
            this.handleFactory = runtime.kBase.newFactHandleFactory();
            this.nodeMemories = new ConcurrentNodeMemories(runtime.kBase, DEFAULT_RULE_UNIT);
            this.pctxFactory = runtime.kBase.getConfiguration().getComponentFactory().getPropagationContextFactory();
            this.agenda = new UnitAgenda( runtime.kBase, this );
            this.timerService = TimerServiceFactory.getTimerService( config );

            initInitialFact( runtime.kBase );
            configListeners( runtime );
        }

        private InternalFactHandle initInitialFact( InternalKnowledgeBase kBase ) {
            InitialFact initialFact = InitialFactImpl.getInstance();
            InternalFactHandle handle = new DefaultFactHandle(0, initialFact, 0, null);

            ObjectTypeNode otn = kBase.getRete().getEntryPointNode( EntryPointId.DEFAULT ).getObjectTypeNodes().get( InitialFact_ObjectType );
            if (otn != null) {
                PropagationContextFactory ctxFact = kBase.getConfiguration().getComponentFactory().getPropagationContextFactory();
                PropagationContext pctx = ctxFact.createPropagationContext( 0, PropagationContext.Type.INSERTION, null,
                        null, handle, EntryPointId.DEFAULT, null );
                otn.assertInitialFact( handle, pctx, this );
            }

            return handle;
        }

        private void configListeners( UnitRuntimeImpl runtime ) {
            if (runtime.application.config() != null && runtime.application.config().rule() != null) {
                RuleEventListenerConfig ruleEventListenerConfig = runtime.application.config().rule().ruleEventListeners();
                ruleEventListenerConfig.agendaListeners().forEach(this::addEventListener);
                ruleEventListenerConfig.ruleRuntimeListeners().forEach(this::addEventListener);
            }
        }

        public Application getApplication() {
            return runtime.application;
        }

        @Override
        public InternalKnowledgeBase getKnowledgeBase() {
            return runtime.kBase;
        }

        @Override
        public InternalKnowledgeRuntime getKnowledgeRuntime() {
            return this;
        }

        @Override
        public SessionConfiguration getSessionConfiguration() {
            return runtime.config;
        }

        public long getNextPropagationIdCounter() {
            return this.propagationIdCounter.incrementAndGet();
        }

        @Override
        public FactHandleFactory getFactHandleFactory() {
            return this.handleFactory;
        }

        @Override
        public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
            return this.ruleRuntimeEventSupport;
        }

        @Override
        public AgendaEventSupport getAgendaEventSupport() {
            return agendaEventSupport;
        }

        @Override
        public RuleEventListenerSupport getRuleEventSupport() {
            return ruleEventListenerSupport;
        }

        @Override
        public void addPropagation(PropagationEntry propagationEntry) {
            agenda.addPropagation( propagationEntry );
        }

        @Override
        public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
            return nodeMemories.getNodeMemory( node, this );
        }

        @Override
        public void clearNodeMemory(final MemoryFactory node) {
            if (nodeMemories != null) nodeMemories.clearNodeMemory( node );
        }

        @Override
        public NodeMemories getNodeMemories() {
            return nodeMemories;
        }

        @Override
        public InternalAgenda getAgenda() {
            return agenda;
        }

        @Override
        public void startOperation() {
        }

        @Override
        public void endOperation() {
        }

        @Override
        public boolean isSequential() {
            return false;
        }

        @Override
        public int fireAllRules() {
            return internalFireAllRules(null, -1);
        }

        private int internalFireAllRules(AgendaFilter agendaFilter, int fireLimit) {
            int fireCount = 0;
            try {
                fireCount = agenda.fireAllRules( agendaFilter, fireLimit );
            } finally {
                if (runtime.kBase.flushModifications()) {
                    fireCount += internalFireAllRules(agendaFilter, fireLimit);
                }
            }
            return fireCount;
        }

        @Override
        public QueryResults getQueryResults( String queryName, Object... arguments ) {
            try {
                this.lock.lock();

                runtime.kBase.executeQueuedActions();
                // it is necessary to flush the propagation queue twice to perform all the expirations
                // eventually enqueued by events that have been inserted when already expired
                agenda.executeFlush();
                agenda.executeFlush();

                DroolsQuery queryObject = new DroolsQuery( queryName, arguments, new NonCloningQueryViewListener(),
                                                           false, null, null, null, null, null );

                InternalFactHandle handle = this.handleFactory.newFactHandle( queryObject, null, this, this );

                final PropagationContext pCtx = pctxFactory.createPropagationContext(getNextPropagationIdCounter(), PropagationContext.Type.INSERTION,
                        null, null, handle, getEntryPoint());


                BaseNode[] tnodes = evalQuery(queryName, queryObject, handle, pCtx);

                List<Map<String, Declaration>> decls = new ArrayList<Map<String, Declaration>>();
                if ( tnodes != null ) {
                    for ( BaseNode node : tnodes ) {
                        decls.add( (( QueryTerminalNode ) node).getSubRule().getOuterDeclarations() );
                    }
                }

                this.handleFactory.destroyFactHandle( handle);

                return new QueryResultsImpl( (List<QueryRowWithSubruleIndex>) queryObject.getQueryResultCollector().getResults(),
                        decls.toArray( new Map[decls.size()] ),
                        this,
                        ( queryObject.getQuery() != null ) ? queryObject.getQuery().getParameters()  : new Declaration[0] );
            } finally {
                this.lock.unlock();
            }
        }

        private BaseNode[] evalQuery(final String queryName, final DroolsQuery queryObject, final InternalFactHandle handle, final PropagationContext pCtx) {
            ExecuteQuery executeQuery = new ExecuteQuery( queryName, queryObject, handle, pCtx);
            addPropagation( executeQuery );
            return executeQuery.getResult();
        }

        private class ExecuteQuery extends PropagationEntry.PropagationEntryWithResult<BaseNode[]> {

            private final String queryName;
            private final DroolsQuery queryObject;
            private final InternalFactHandle handle;
            private final PropagationContext pCtx;

            private ExecuteQuery( String queryName, DroolsQuery queryObject, InternalFactHandle handle, PropagationContext pCtx ) {
                this.queryName = queryName;
                this.queryObject = queryObject;
                this.handle = handle;
                this.pCtx = pCtx;
            }

            @Override
            public void execute( InternalWorkingMemory wm ) {
                BaseNode[] tnodes = wm.getKnowledgeBase().getReteooBuilder().getTerminalNodesForQuery( queryName );
                if ( tnodes == null ) {
                    throw new RuntimeException( "Query '" + queryName + "' does not exist" );
                }

                QueryTerminalNode tnode = (QueryTerminalNode) tnodes[0];

                if (queryObject.getElements().length != tnode.getQuery().getParameters().length) {
                    throw new RuntimeException( "Query '" + queryName + "' has been invoked with a wrong number of arguments. Expected " +
                            tnode.getQuery().getParameters().length + ", actual " + queryObject.getElements().length );
                }

                LeftTupleSource lts = tnode.getLeftTupleSource();
                while ( lts.getType() != NodeTypeEnums.LeftInputAdapterNode ) {
                    lts = lts.getLeftTupleSource();
                }
                LeftInputAdapterNode lian = (LeftInputAdapterNode) lts;
                LeftInputAdapterNode.LiaNodeMemory lmem = getNodeMemory( lian );
                if ( lmem.getSegmentMemory() == null ) {
                    SegmentUtilities.createSegmentMemory( lts, wm );
                }

                LeftInputAdapterNode.doInsertObject( handle, pCtx, lian, wm, lmem, false, queryObject.isOpen() );

                for ( PathMemory rm : lmem.getSegmentMemory().getPathMemories() ) {
                    RuleAgendaItem evaluator = agenda.createRuleAgendaItem( Integer.MAX_VALUE, rm, (TerminalNode) rm.getPathEndNode() );
                    evaluator.getRuleExecutor().setDirty( true );
                    evaluator.getRuleExecutor().evaluateNetworkAndFire( wm, null, 0, -1 );
                }

                done(tnodes);
            }

            @Override
            public boolean isCalledFromRHS() {
                return false;
            }
        }

        @Override
        public EntryPointId getEntryPoint() {
            return EntryPointId.DEFAULT;
        }

        @Override
        public SessionClock getSessionClock() {
            return (SessionClock) this.timerService;
        }

        @Override
        public void setGlobal( String identifier, Object value ) {
            // Cannot set null values
            if ( value == null ) {
                return;
            }

            try {
                runtime.kBase.readLock();
                // Make sure the global has been declared in the RuleBase
                Class type = runtime.kBase.getGlobals().get( identifier );
                if ( (type == null) ) {
                    throw new RuntimeException( "Unexpected global [" + identifier + "]" );
                } else if ( !type.isInstance( value ) ) {
                    throw new RuntimeException( "Illegal class for global. " + "Expected [" + type.getName() + "], " + "found [" + value.getClass().getName() + "]." );

                } else {
                    this.globalResolver.setGlobal( identifier, value );
                }
            } finally {
                runtime.kBase.readUnlock();
            }
        }

        @Override
        public Object getGlobal( String identifier ) {
            return this.globalResolver.resolveGlobal( identifier );
        }

        @Override
        public Globals getGlobals() {
            return (Globals) this.getGlobalResolver();
        }

        @Override
        public void addEventListener(final RuleRuntimeEventListener listener) {
            this.ruleRuntimeEventSupport.addEventListener( listener );
        }

        @Override
        public void removeEventListener(final RuleRuntimeEventListener listener) {
            this.ruleRuntimeEventSupport.removeEventListener( listener );
        }

        @Override
        public void addEventListener(final AgendaEventListener listener) {
            this.agendaEventSupport.addEventListener( listener );
        }

        @Override
        public void removeEventListener(final AgendaEventListener listener) {
            this.agendaEventSupport.removeEventListener( listener );
        }

        @Override
        public Environment getEnvironment() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getEnvironment -> TODO" );

        }

        @Override
        public KieBase getKieBase() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getKieBase -> TODO" );

        }

        @Override
        public void registerChannel( String name, Channel channel ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.registerChannel -> TODO" );

        }

        @Override
        public void unregisterChannel( String name ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.unregisterChannel -> TODO" );

        }

        @Override
        public void setGlobalResolver( GlobalResolver globalResolver ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setGlobalResolver -> TODO" );

        }

        @Override
        public GlobalResolver getGlobalResolver() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getGlobalResolver -> TODO" );

        }

        @Override
        public void delete( FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.delete -> TODO" );

        }

        @Override
        public void delete( FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.delete -> TODO" );

        }

        @Override
        public void update( FactHandle handle, Object object, BitMask mask, Class<?> modifiedClass, Activation activation ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.update -> TODO" );

        }

        @Override
        public TruthMaintenanceSystem getTruthMaintenanceSystem() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getTruthMaintenanceSystem -> TODO" );

        }

        @Override
        public int fireAllRules( AgendaFilter agendaFilter ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.fireAllRules -> TODO" );

        }

        @Override
        public int fireAllRules( int fireLimit ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.fireAllRules -> TODO" );

        }

        @Override
        public int fireAllRules( AgendaFilter agendaFilter, int fireLimit ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.fireAllRules -> TODO" );

        }

        @Override
        public Object getObject( FactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getObject -> TODO" );

        }

        @Override
        public Collection<? extends Object> getObjects() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getObjects -> TODO" );

        }

        @Override
        public Collection<? extends Object> getObjects( ObjectFilter filter ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getObjects -> TODO" );

        }

        @Override
        public <T extends FactHandle> Collection<T> getFactHandles() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getFactHandles -> TODO" );

        }

        @Override
        public <T extends FactHandle> Collection<T> getFactHandles( ObjectFilter filter ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getFactHandles -> TODO" );

        }

        @Override
        public long getFactCount() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getFactCount -> TODO" );

        }

        @Override
        public String getEntryPointId() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getEntryPointId -> TODO" );

        }

        @Override
        public FactHandle insert( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.insert -> TODO" );

        }

        @Override
        public void retract( FactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.retract -> TODO" );

        }

        @Override
        public void delete( FactHandle handle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.delete -> TODO" );

        }

        @Override
        public void delete( FactHandle handle, FactHandle.State fhState ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.delete -> TODO" );

        }

        @Override
        public void update( FactHandle handle, Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.update -> TODO" );

        }

        @Override
        public void update( FactHandle handle, Object object, String... modifiedProperties ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.update -> TODO" );

        }

        @Override
        public FactHandle getFactHandle( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getFactHandle -> TODO" );

        }

        @Override
        public long getIdentifier() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getIdentifier -> TODO" );

        }

        @Override
        public void setIdentifier( long id ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setIdentifier -> TODO" );

        }

        @Override
        public void setEndOperationListener( EndOperationListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setEndOperationListener -> TODO" );

        }

        @Override
        public long getLastIdleTimestamp() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getLastIdleTimestamp -> TODO" );

        }

        @Override
        public void setRuleRuntimeEventSupport( RuleRuntimeEventSupport workingMemoryEventSupport ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setRuleRuntimeEventSupport -> TODO" );

        }

        @Override
        public void setAgendaEventSupport( AgendaEventSupport agendaEventSupport ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setAgendaEventSupport -> TODO" );

        }

        @Override
        public ObjectStore getObjectStore() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getObjectStore -> TODO" );

        }

        @Override
        public FactHandleFactory getHandleFactory() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getHandleFactory -> TODO" );

        }

        @Override
        public void queueWorkingMemoryAction( WorkingMemoryAction action ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.queueWorkingMemoryAction -> TODO" );

        }

        @Override
        public InternalWorkingMemory getInternalWorkingMemory() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getInternalWorkingMemory -> TODO" );

        }

        @Override
        public EntryPointNode getEntryPointNode() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getEntryPointNode -> TODO" );

        }

        @Override
        public EntryPoint getEntryPoint( String name ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getEntryPoint -> TODO" );

        }

        @Override
        public FactHandle getFactHandleByIdentity( Object object ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getFactHandleByIdentity -> TODO" );

        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.reset -> TODO" );

        }

        @Override
        public Iterator<?> iterateObjects() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.iterateObjects -> TODO" );

        }

        @Override
        public Iterator<?> iterateObjects( ObjectFilter filter ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.iterateObjects -> TODO" );

        }

        @Override
        public Iterator<InternalFactHandle> iterateFactHandles() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.iterateFactHandles -> TODO" );

        }

        @Override
        public Iterator<InternalFactHandle> iterateFactHandles( ObjectFilter filter ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.iterateFactHandles -> TODO" );

        }

        @Override
        public void setFocus( String focus ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setFocus -> TODO" );

        }

        @Override
        public LiveQuery openLiveQuery( String query, Object[] arguments, ViewChangedEventListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.openLiveQuery -> TODO" );

        }

        @Override
        public void setAsyncExceptionHandler( AsyncExceptionHandler handler ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.setAsyncExceptionHandler -> TODO" );

        }

        @Override
        public void clearAgenda() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.clearAgenda -> TODO" );

        }

        @Override
        public void clearAgendaGroup( String group ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.clearAgendaGroup -> TODO" );

        }

        @Override
        public void clearActivationGroup( String group ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.clearActivationGroup -> TODO" );

        }

        @Override
        public void clearRuleFlowGroup( String group ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.clearRuleFlowGroup -> TODO" );

        }

        @Override
        public ProcessInstance startProcess( String processId ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.startProcess -> TODO" );

        }

        @Override
        public ProcessInstance startProcess( String processId, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.startProcess -> TODO" );

        }

        @Override
        public ProcessInstance createProcessInstance( String processId, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.createProcessInstance -> TODO" );

        }

        @Override
        public ProcessInstance startProcessInstance( String processInstanceId ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.startProcessInstance -> TODO" );

        }

        @Override
        public ProcessInstance startProcessInstance( String processInstanceId, String trigger ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.startProcessInstance -> TODO" );

        }

        @Override
        public void signalEvent( String type, Object event ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.signalEvent -> TODO" );

        }

        @Override
        public void signalEvent( String type, Object event, String processInstanceId ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.signalEvent -> TODO" );

        }

        @Override
        public Collection<ProcessInstance> getProcessInstances() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getProcessInstances -> TODO" );

        }

        @Override
        public ProcessInstance getProcessInstance( String id ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getProcessInstance -> TODO" );

        }

        @Override
        public ProcessInstance getProcessInstance( String id, boolean readOnly ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getProcessInstance -> TODO" );

        }

        @Override
        public void abortProcessInstance( String processInstanceId ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.abortProcessInstance -> TODO" );

        }

        @Override
        public WorkItemManager getWorkItemManager() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getWorkItemManager -> TODO" );

        }

        @Override
        public JobsService getJobsService() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getJobsService -> TODO" );

        }

        @Override
        public void halt() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.halt -> TODO" );

        }

        @Override
        public FactHandle insert( Object object, boolean dynamic ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.insert -> TODO" );

        }

        @Override
        public WorkingMemoryEntryPoint getWorkingMemoryEntryPoint( String id ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getWorkingMemoryEntryPoint -> TODO" );

        }

        @Override
        public void dispose() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.dispose -> TODO" );

        }

        @Override
        public Lock getLock() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getLock -> TODO" );

        }

        @Override
        public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getObjectTypeConfigurationRegistry -> TODO" );

        }

        @Override
        public InternalFactHandle getInitialFactHandle() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getInitialFactHandle -> TODO" );

        }

        @Override
        public Calendars getCalendars() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getCalendars -> TODO" );

        }

        @Override
        public TimerService getTimerService() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getTimerService -> TODO" );

        }

        @Override
        public Map<String, Channel> getChannels() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getChannels -> TODO" );

        }

        @Override
        public Collection<? extends EntryPoint> getEntryPoints() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getEntryPoints -> TODO" );

        }

        @Override
        public void startBatchExecution() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.startBatchExecution -> TODO" );

        }

        @Override
        public void endBatchExecution() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.endBatchExecution -> TODO" );

        }

        @Override
        public void executeQueuedActions() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.executeQueuedActions -> TODO" );

        }

        @Override
        public long getIdleTime() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getIdleTime -> TODO" );

        }

        @Override
        public long getTimeToNextJob() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getTimeToNextJob -> TODO" );

        }

        @Override
        public void updateEntryPointsCache() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.updateEntryPointsCache -> TODO" );

        }

        @Override
        public void prepareToFireActivation() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.prepareToFireActivation -> TODO" );

        }

        @Override
        public void activationFired() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.activationFired -> TODO" );

        }

        @Override
        public long getTotalFactCount() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getTotalFactCount -> TODO" );

        }

        @Override
        public InternalProcessRuntime getProcessRuntime() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getProcessRuntime -> TODO" );

        }

        @Override
        public InternalProcessRuntime internalGetProcessRuntime() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.internalGetProcessRuntime -> TODO" );

        }

        @Override
        public void closeLiveQuery( InternalFactHandle factHandle ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.closeLiveQuery -> TODO" );

        }

        @Override
        public void flushPropagations() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.flushPropagations -> TODO" );

        }

        @Override
        public void activate() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.activate -> TODO" );

        }

        @Override
        public void deactivate() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.deactivate -> TODO" );

        }

        @Override
        public boolean tryDeactivate() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.tryDeactivate -> TODO" );

        }

        @Override
        public Iterator<? extends PropagationEntry> getActionsIterator() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getActionsIterator -> TODO" );

        }

        @Override
        public void removeGlobal( String identifier ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.removeGlobal -> TODO" );

        }

        @Override
        public void notifyWaitOnRest() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.notifyWaitOnRest -> TODO" );

        }

        @Override
        public void cancelActivation( Activation activation, boolean declarativeAgenda ) {
            if (declarativeAgenda) {
                throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.cancelActivation -> TODO" );
            }
        }

        @Override
        public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getRuleRuntimeEventListeners -> TODO" );

        }

        @Override
        public Collection<AgendaEventListener> getAgendaEventListeners() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getAgendaEventListeners -> TODO" );

        }

        @Override
        public void addEventListener( KieBaseEventListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.addEventListener -> TODO" );

        }

        @Override
        public void removeEventListener( KieBaseEventListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.removeEventListener -> TODO" );

        }

        @Override
        public Collection<KieBaseEventListener> getKieBaseEventListeners() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getKieBaseEventListeners -> TODO" );

        }

        @Override
        public KieRuntimeLogger getLogger() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getLogger -> TODO" );

        }

        @Override
        public void addEventListener( ProcessEventListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.addEventListener -> TODO" );

        }

        @Override
        public void removeEventListener( ProcessEventListener listener ) {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.removeEventListener -> TODO" );

        }

        @Override
        public Collection<ProcessEventListener> getProcessEventListeners() {
            throw new UnsupportedOperationException( "org.kie.kogito.rules.units.impl.UnitRuntimeImpl.WorkingMemoryAdapter.getProcessEventListeners -> TODO" );

        }
    }
}
