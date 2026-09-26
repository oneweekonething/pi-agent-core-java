package com.earendil.pi.session;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class SessionsTest {
    @Test public void branchPreservesHistory(){
        Sessions.Tree tree=new Sessions.Tree("s");
        Sessions.Node first=tree.appendUser("one");
        Sessions.Node second=tree.appendAssistant("two",null);
        tree.branchFrom(first.getId(),"alt");
        assertEquals(2,tree.activePath().size());
        assertEquals(3,tree.allNodes().size());
        tree.rewindTo(second.getId());
        assertEquals("two",tree.activePath().get(1).getContent());
    }

    @Test public void concurrentGetOrCreateYieldsSingleTree() throws Exception {
        final Sessions.Manager manager=new Sessions.Manager(new Sessions.InMemoryRepository());
        int threads=8;
        final CountDownLatch start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(threads);
        try{
            List<Future<Sessions.Tree>> futures=new ArrayList<Future<Sessions.Tree>>();
            for(int i=0;i<threads;i++){
                futures.add(pool.submit(new java.util.concurrent.Callable<Sessions.Tree>(){
                    public Sessions.Tree call() throws Exception {
                        start.await();
                        return manager.getOrCreate("shared").join();
                    }
                }));
            }
            start.countDown();
            Set<String> ids=new HashSet<String>();
            for(Future<Sessions.Tree> future:futures)ids.add(future.get().getId());
            assertEquals(1,ids.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
