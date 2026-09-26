package com.earendil.pi.session;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test public void asyncGetOrCreateDeduplicatesInFlightCreation() throws Exception {
        final List<CompletableFuture<Optional<Sessions.Tree>>> finds=new ArrayList<CompletableFuture<Optional<Sessions.Tree>>>();
        final List<Sessions.Tree> saved=new ArrayList<Sessions.Tree>();
        Sessions.Repository slow=new Sessions.Repository(){
            public CompletableFuture<Optional<Sessions.Tree>> find(String id){
                CompletableFuture<Optional<Sessions.Tree>> future=new CompletableFuture<Optional<Sessions.Tree>>();
                finds.add(future);
                return future;
            }
            public CompletableFuture<Void> save(Sessions.Tree tree){
                saved.add(tree);
                return CompletableFuture.completedFuture(null);
            }
        };
        Sessions.Manager manager=new Sessions.Manager(slow);
        CompletableFuture<Sessions.Tree> first=manager.getOrCreate("s");
        CompletableFuture<Sessions.Tree> second=manager.getOrCreate("s");
        finds.get(0).complete(Optional.<Sessions.Tree>empty());
        assertEquals(1,finds.size());
        assertEquals(1,saved.size());
        assertEquals(first.join(),second.join());
        assertTrue(singleFlightEmpty(manager,"s"));
    }

    @Test public void failedCreationIsRetriable() throws Exception {
        final java.util.concurrent.atomic.AtomicInteger saves=new java.util.concurrent.atomic.AtomicInteger();
        Sessions.Repository flaky=new Sessions.Repository(){
            public CompletableFuture<Optional<Sessions.Tree>> find(String id){
                return CompletableFuture.completedFuture(Optional.<Sessions.Tree>empty());
            }
            public CompletableFuture<Void> save(Sessions.Tree tree){
                if(saves.incrementAndGet()==1){
                    CompletableFuture<Void> failed=new CompletableFuture<Void>();
                    failed.completeExceptionally(new IllegalStateException("write failed"));
                    return failed;
                }
                return CompletableFuture.completedFuture(null);
            }
        };
        Sessions.Manager manager=new Sessions.Manager(flaky);
        Throwable first=manager.getOrCreate("s").handle((t,e)->e).join();
        assertTrue(first instanceof IllegalStateException);
        assertTrue(singleFlightEmpty(manager,"s"));
        assertEquals("s",manager.getOrCreate("s").join().getId());
        assertTrue(singleFlightEmpty(manager,"s"));
    }

    private static boolean singleFlightEmpty(Sessions.Manager manager,String id) throws Exception {
        java.lang.reflect.Field field=Sessions.Manager.class.getDeclaredField("creations");
        field.setAccessible(true);
        Object map=field.get(manager);
        return ((java.util.Map<?,?>)map).isEmpty();
    }
}
