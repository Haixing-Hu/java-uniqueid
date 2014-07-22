package org.lable.util.uniqueid.zookeeper;


import org.apache.zookeeper.ZooKeeper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.lable.util.uniqueid.GeneratorException;
import org.lable.util.uniqueid.UniqueIDGenerator;

import java.io.IOException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.lable.util.uniqueid.zookeeper.ResourceTestPoolHelper.prepareClusterID;
import static org.lable.util.uniqueid.zookeeper.ResourceTestPoolHelper.prepareEmptyQueueAndPool;

public class ResourceClaimIT {

    @Rule
    public ZooKeeperInstance zkInstance = new ZooKeeperInstance();

    @Before
    public void before() throws Exception {
        ZooKeeperConnection.configure(zkInstance.getQuorumAddresses());
        ZooKeeper zookeeper = zkInstance.getZookeeperConnection();
        prepareClusterID(zookeeper, 3);
        prepareEmptyQueueAndPool(zookeeper);
        ZooKeeperConnection.reset();
    }

    @Test
    public void claimTest() throws Exception {
        ResourceClaim claim = ResourceClaim.claim(ZooKeeperConnection.get(), 2);
        int resource = claim.get();
        assertThat(resource, is(both(greaterThanOrEqualTo(0)).and(lessThan(2))));
    }

    @Test
    public void concurrencyTest() throws Exception {
        final int threadCount = 20;
        final int poolSize = 64;

        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final ConcurrentMap<Integer, Integer> result = new ConcurrentHashMap<Integer, Integer>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Integer number = 10 + i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        ResourceClaim claim = ResourceClaim.claim(ZooKeeperConnection.get(), poolSize);
                        result.put(number, claim.get());
                    } catch (IOException e) {
                        fail();
                    } catch (InterruptedException e) {
                        fail();
                    }
                    done.countDown();
                }
            }, String.valueOf(number)).start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertThat(result.size(), is(threadCount));

        Set<Integer> allResources = new HashSet<Integer>();
        allResources.addAll(result.values());
        assertThat(allResources.size(), is(threadCount));
    }

    @Test
    public void concurrencyLimitedPoolTest() throws Exception {
        final int threadCount = 20;
        final int poolSize = 1;

        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final ConcurrentMap<Integer, Integer> result = new ConcurrentHashMap<Integer, Integer>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Integer number = 10 + i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        ResourceClaim claim = ResourceClaim.claim(ZooKeeperConnection.get(), poolSize);
                        result.put(number, claim.get());
                        claim.close();
                    } catch (IOException e) {
                        fail();
                    } catch (InterruptedException e) {
                        fail();
                    }
                    done.countDown();
                }
            }, String.valueOf(number)).start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertThat(result.size(), is(threadCount));

        Set<Integer> allResources = new HashSet<Integer>();
        allResources.addAll(result.values());
        assertThat(allResources.size(), is(1));
    }

    @Test
    @Ignore
    public void testAgainstRealQuorum() throws Exception {
        ZooKeeperConnection.configure("zka,zkb,zkc");
        claimTest();
    }
}