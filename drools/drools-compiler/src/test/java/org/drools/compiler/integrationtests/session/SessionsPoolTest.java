/*
 * Copyright 2018 JBoss Inc
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

package org.drools.compiler.integrationtests.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieContainerSessionsPool;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionsPool;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;
import org.kie.internal.conf.SequentialOption;
import org.kie.internal.utils.KieHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SessionsPoolTest {

    @Test
    public void testKieSessionsPool() {
        KieContainerSessionsPool pool = getKieContainer().newKieSessionsPool( 1 );

        KieSession ksession = pool.newKieSession();
        try {
            checkKieSession( ksession );
        } finally {
            ksession.dispose();
        }

        try {
            ksession.insert( "test2" );
            fail("it shouldn't be possible to operate on a disposed session even if created from a pool");
        } catch (Exception e) { }

        KieSession ksession2 = pool.newKieSession();

        // using a pool with only one session so it should return the same one as before
        assertSame( ksession, ksession2 );
        assertNull( ksession2.getGlobal( "list" ) );
        checkKieSession( ksession2 );

        pool.shutdown();

        try {
            ksession.insert( "test3" );
            fail("after pool shutdown all sessions created from it should be disposed");
        } catch (IllegalStateException e) { }

        try {
            pool.newKieSession();
            fail("after pool shutdown it shouldn't be possible to get sessions from it");
        } catch (IllegalStateException e) { }
    }

    @Test
    public void testKieSessionsPoolInMultithreadEnv() throws InterruptedException, ExecutionException {
        KieContainerSessionsPool pool = getKieContainer().newKieSessionsPool( 4 );

        final int THREAD_NR = 10;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NR, r -> {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        try {
            CompletionService<Boolean> ecs = new ExecutorCompletionService<>( executor );
            for (int i = 0; i < THREAD_NR; i++) {
                ecs.submit( () -> {
                    try {
                        KieSession ksession = pool.newKieSession();
                        try {
                            checkKieSession( ksession );
                        } finally {
                            ksession.dispose();
                        }
                        return true;
                    } catch (final Exception e) {
                        return false;
                    }
                } );
            }
            boolean success = true;
            for (int i = 0; i < THREAD_NR; i++) {
                success = ecs.take().get() && success;
            }
            assertTrue( success );
        } finally {
            executor.shutdown();
        }

        pool.shutdown();
        try {
            pool.newKieSession();
            fail("after pool shutdown it shouldn't be possible to get sessions from it");
        } catch (IllegalStateException e) { }
    }

    @Test
    public void testStatelessKieSessionsPool() {
        KieContainerSessionsPool pool = getKieContainer().newKieSessionsPool( 1 );
        StatelessKieSession session = pool.newStatelessKieSession();

        List<String> list = new ArrayList<>();
        session.setGlobal( "list", list );
        session.execute( "test" );
        assertEquals(1, list.size());

        list.clear();
        session.execute( "test" );
        assertEquals(1, list.size());
    }

    private KieContainer getKieContainer() {
        String drl =
                "global java.util.List list\n" +
                "rule R1 when\n" +
                "  $s: String()\n" +
                "then\n" +
                "  list.add($s);\n" +
                "end\n";

        return new KieHelper().addContent( drl, ResourceType.DRL ).getKieContainer();
    }

    private void checkKieSession( KieSession ksession ) {
        List<String> list = new ArrayList<>();
        ksession.setGlobal( "list", list );
        ksession.insert( "test" );
        ksession.fireAllRules();
        assertEquals(1, list.size());
    }

    @Test
    public void testSegmentMemoriesReset() {
        // DROOLS-3228
        String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list\n" +
                "rule R1 when\n" +
                "  String()\n" +
                "  $i : AtomicInteger()\n" +
                "  not Boolean()\n" +
                "then\n" +
                "  insert(true);\n" +
                "  insert($i.incrementAndGet());\n" +
                "end\n" +
                "\n" +
                "rule R2 when \n" +
                "  String()\n" +
                "  $i : AtomicInteger()\n" +
                "then\n" +
                "end\n" +
                "\n" +
                "rule R3 when\n" +
                "  Integer( this > 2 )\n" +
                "then\n" +
                "  list.add(\"OK\");\n" +
                "end";

        KieContainer kcontainer = new KieHelper().addContent( drl, ResourceType.DRL ).getKieContainer();
        KieSessionsPool pool = kcontainer.newKieSessionsPool( 1 );

        AtomicInteger i = new AtomicInteger(1);

        KieSession ksession = pool.newKieSession();
        List<String> list = new ArrayList<>();
        ksession.setGlobal( "list", list );

        ksession.insert( i );
        ksession.insert( "test" );
        ksession.fireAllRules();

        ksession.dispose();

        assertEquals(0, list.size());

        ksession = pool.newKieSession();

        ksession.setGlobal( "list", list );
        ksession.insert( i );
        ksession.insert( "test" );
        ksession.fireAllRules();

        ksession.dispose();

        assertEquals(1, list.size());

        pool.shutdown();
    }

    @Test
    public void testStatelessSequential() {
        // DROOLS-3228
        String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list\n" +
                "rule R1 when\n" +
                "  String()\n" +
                "  Integer()\n" +
                "then\n" +
                "  list.add(\"OK\");\n" +
                "end";

        KieBase kbase = new KieHelper().addContent( drl, ResourceType.DRL ).build( SequentialOption.YES );
        KieSessionsPool pool = kbase.newKieSessionsPool( 1 );

        StatelessKieSession ksession = pool.newStatelessKieSession();

        List<String> list = new ArrayList<>();

        List<Command> commands = new ArrayList<>(5);
        commands.add(CommandFactory.newSetGlobal("list", list));
        commands.add(CommandFactory.newInsert("test"));
        commands.add(CommandFactory.newInsert(1));
        commands.add(CommandFactory.newFireAllRules());
        ksession.execute(CommandFactory.newBatchExecution(commands));

        assertEquals(1, list.size());

        list.clear();

        ksession.execute(CommandFactory.newBatchExecution(commands));

        assertEquals(1, list.size());

        pool.shutdown();
    }
}
