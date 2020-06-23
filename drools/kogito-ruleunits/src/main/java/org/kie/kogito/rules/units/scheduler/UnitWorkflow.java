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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.kie.internal.concurrent.ExecutorProviderFactory;
import org.kie.kogito.rules.RuleUnitInstance;

public interface UnitWorkflow {

    void execute();


    class Single implements UnitWorkflow {

        private final RuleUnitInstance<?> unit;

        public Single( RuleUnitInstance<?> unit ) {
            this.unit = unit;
        }

        @Override
        public void execute() {
            unit.fire();
        }
    }

    class Multiple implements UnitWorkflow {
        private final ExecutorService executor = ExecutorProviderFactory.getExecutorProvider().getExecutor();

        protected final List<UnitWorkflow> units;
        private final boolean parallel;

        public Multiple( List<UnitWorkflow> units, boolean parallel ) {
            this.units = units;
            this.parallel = parallel;
        }

        @Override
        public void execute() {
            if (units.size() == 0) {
                units.get(0).execute();
            } else if (parallel) {
                CountDownLatch latch = new CountDownLatch(units.size());
                for (UnitWorkflow unit : units) {
                    executor.submit( () -> {
                        unit.execute();
                        latch.countDown();
                    } );
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException( e );
                }
            } else {
                for (UnitWorkflow unit : units) {
                    unit.execute();
                }
            }
        }
    }
}
