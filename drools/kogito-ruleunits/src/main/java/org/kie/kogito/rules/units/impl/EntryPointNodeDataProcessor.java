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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.ClassAwareObjectStore;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.impl.InternalDataProcessor;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.spi.Activation;
import org.drools.core.spi.PropagationContext;
import org.drools.core.util.bitmask.BitMask;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.kogito.rules.DataHandle;

import static org.drools.core.reteoo.PropertySpecificUtil.allSetBitMask;

public class EntryPointNodeDataProcessor implements InternalDataProcessor {
    private EntryPoint entryPoint;
    private final EntryPointNode epNode;
    private final InternalWorkingMemory workingMemory;

    private final Map<DataHandle, InternalFactHandle> handles = new HashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final ObjectStore objectStore;

    private final PropagationContextFactory pctxFactory;

    public EntryPointNodeDataProcessor( EntryPointNode epNode, InternalWorkingMemory workingMemory ) {
        this.epNode = epNode;
        this.workingMemory = workingMemory;
        this.objectStore = new ClassAwareObjectStore(epNode.getKnowledgeBase().getConfiguration(), this.lock);
        this.pctxFactory = epNode.getKnowledgeBase().getConfiguration().getComponentFactory().getPropagationContextFactory();
    }

    // -- Insert --------------

    @Override
    public FactHandle insert(DataHandle handle, Object object) {
        InternalFactHandle fh = internalInsert( object );
        if (handle != null) {
            handles.put( handle, fh );
        }
        return fh;
    }

    private InternalFactHandle internalInsert(Object object) {
        if ( object == null ) {
            // you cannot assert a null object
            return null;
        }

        try {
            ObjectTypeConf typeConf = epNode.getTypeConfReg().getObjectTypeConf( epNode.getEntryPoint(), object );

            final PropagationContext propagationContext = pctxFactory.createPropagationContext(workingMemory.getNextPropagationIdCounter(),
                    PropagationContext.Type.INSERTION, null, null, null, epNode.getEntryPoint());

            this.lock.lock();

            // check if the object already exists in the WM
            InternalFactHandle handle = this.objectStore.getHandleForObject( object );
            if ( handle != null ) {
                return handle;
            }

            handle = createHandle( object, typeConf );

            propagationContext.setFactHandle(handle);

            internalInsert( handle, object, typeConf, propagationContext );

            return handle;
        } finally {
            this.lock.unlock();
        }
    }

    public void internalInsert(InternalFactHandle handle, Object object, ObjectTypeConf typeConf, PropagationContext pctx) {
        epNode.getKnowledgeBase().executeQueuedActions();
        objectStore.addHandle( handle, object );
        epNode.assertObject( handle, pctx, typeConf, workingMemory );
        workingMemory.getRuleRuntimeEventSupport().fireObjectInserted(pctx, handle, object, workingMemory);
    }

    private InternalFactHandle createHandle(Object object, ObjectTypeConf typeConf) {
        return workingMemory.getFactHandleFactory().newFactHandle( object, typeConf, workingMemory, null );
    }

    // -- Update --------------

    @Override
    public void update( DataHandle dh, Object obj, BitMask mask, Class<?> modifiedClass, Activation activation) {
        update( handles.get(dh), obj, mask, modifiedClass, activation );
    }

    @Override
    public void update( InternalFactHandle fh, Object obj, BitMask mask, Class<?> modifiedClass, Activation activation) {
        internalUpdate( fh, obj, mask, modifiedClass, activation );
    }

    @Override
    public void update(DataHandle handle, Object object) {
        internalUpdate( handles.get(handle), object, allSetBitMask(), Object.class, null );
    }

    private InternalFactHandle internalUpdate(InternalFactHandle handle, Object object, BitMask mask, Class<?> modifiedClass, Activation activation) {
        this.lock.lock();
        try {
            epNode.getKnowledgeBase().executeQueuedActions();

            // the handle might have been disconnected, so reconnect if it has
            if (handle.isDisconnected()) {
                handle = this.objectStore.reconnect(handle);
            }

            final Object originalObject = handle.getObject();

            final ObjectTypeConf typeConf = epNode.getTypeConfReg().getObjectTypeConf(epNode.getEntryPoint(), object);

            if (originalObject != object || !RuleBaseConfiguration.AssertBehaviour.IDENTITY.equals(epNode.getKnowledgeBase().getConfiguration().getAssertBehaviour())) {
                this.objectStore.updateHandle(handle, object);
            }

            workingMemory.getFactHandleFactory().increaseFactHandleRecency(handle);

            final PropagationContext propagationContext = pctxFactory.createPropagationContext(workingMemory.getNextPropagationIdCounter(), PropagationContext.Type.MODIFICATION,
                    activation == null ? null : activation.getRule(),
                    activation == null ? null : activation.getTuple().getTupleSink(),
                    handle, epNode.getEntryPoint(), mask, modifiedClass, null);

            internalUpdate(handle, object, originalObject, typeConf, propagationContext);
        } finally {
            this.lock.unlock();
        }
        return handle;
    }

    private void internalUpdate(InternalFactHandle handle, Object object, Object originalObject, ObjectTypeConf typeConf, PropagationContext propagationContext) {
        epNode.modifyObject( handle, propagationContext, typeConf, workingMemory );
        workingMemory.getRuleRuntimeEventSupport().fireObjectUpdated(propagationContext, handle, originalObject, object, workingMemory);
    }

    // -- Delete --------------

    @Override
    public void delete(DataHandle handle) {
        internalDelete(handles.remove(handle), null, null);
    }

    @Override
    public void delete(DataHandle dh, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState) {
        internalDelete( handles.remove(dh), rule, terminalNode );
    }

    @Override
    public void delete(InternalFactHandle fh, RuleImpl rule, TerminalNode terminalNode, FactHandle.State fhState) {
        internalDelete( fh, rule, terminalNode );
        handles.remove( fh.getDataHandle() );
    }

    private void internalDelete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode) {
        if ( factHandle == null ) {
            throw new IllegalArgumentException( "FactHandle cannot be null " );
        }

        this.lock.lock();
        try {
            epNode.getKnowledgeBase().executeQueuedActions();

            InternalFactHandle handle = (InternalFactHandle) factHandle;

            if (handle.getId() == -1) {
                // can't retract an already retracted handle
                return;
            }

            // the handle might have been disconnected, so reconnect if it has
            if (handle.isDisconnected()) {
                handle = this.objectStore.reconnect(handle);
            }

            Object object = handle.getObject();
            ObjectTypeConf typeConf = epNode.getTypeConfReg().getObjectTypeConf( epNode.getEntryPoint(), object );

            PropagationContext propagationContext = pctxFactory.createPropagationContext( workingMemory.getNextPropagationIdCounter(), PropagationContext.Type.DELETION,
                    rule, terminalNode, handle, epNode.getEntryPoint() );
            epNode.retractObject( handle, propagationContext, typeConf, workingMemory );
            objectStore.removeHandle( handle );
            workingMemory.getRuleRuntimeEventSupport().fireObjectRetracted(propagationContext, handle, object, workingMemory);
            workingMemory.getFactHandleFactory().destroyFactHandle( handle );
        } finally {
            this.lock.unlock();
        }
    }
}
