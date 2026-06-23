package org.lionsoul.ip2region.merge.ip;

import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

import java.util.List;

public class RateLimitedRoundRobinIpHelper implements IpHelper {
    private final List<IpHelper> helpers;
    private final long intervalMillis;
    private final Clock clock;
    private final Sleeper sleeper;
    private int index;
    private long lastRequestTime = -1L;

    public RateLimitedRoundRobinIpHelper(List<IpHelper> helpers, long intervalMillis) {
        this(helpers, intervalMillis, new SystemClock(), new ThreadSleeper());
    }

    RateLimitedRoundRobinIpHelper(List<IpHelper> helpers, long intervalMillis, Clock clock, Sleeper sleeper) {
        if (helpers == null || helpers.isEmpty()) {
            throw new IllegalArgumentException("helpers must not be empty");
        }
        this.helpers = helpers;
        this.intervalMillis = intervalMillis;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public synchronized AreaIsp queryIp(String ip) {
        waitIfNeeded();
        IpHelper helper = helpers.get(index);
        index = (index + 1) % helpers.size();
        lastRequestTime = clock.currentTimeMillis();
        return helper.queryIp(ip);
    }

    private void waitIfNeeded() {
        if (lastRequestTime < 0) {
            return;
        }
        long elapsed = clock.currentTimeMillis() - lastRequestTime;
        long waitMillis = intervalMillis - elapsed;
        if (waitMillis > 0) {
            sleeper.sleep(waitMillis);
        }
    }

    interface Clock {
        long currentTimeMillis();
    }

    interface Sleeper {
        void sleep(long millis);
    }

    private static class SystemClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    private static class ThreadSleeper implements Sleeper {
        @Override
        public void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
