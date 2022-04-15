package org.apache.dubbo.proxy.utils;

import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.common.timer.Timeout;
import org.apache.dubbo.common.timer.TimerTask;
import org.apache.dubbo.common.utils.NamedThreadFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/18
 */
public class TimeoutFuture<T> extends CompletableFuture<T> {
    private static final HashedWheelTimer TIMER = new HashedWheelTimer(new NamedThreadFactory("proxy-timeout-timer", true));

    public TimeoutFuture(long timeoutMs) {
        TIMER.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isCancelled() || timeout.timer().isStop() || isDone() || isCancelled()) {
                    // other thread cancel this timeout or stop the timer.
                    return;
                }
                completeExceptionally(new TimeoutException("TimeoutFuture time out exception timeoutMs is :" + timeoutMs));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }
}
