package com.aesean.blockutils;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Printer;

/**
 * BlockUtils
 * 原理大致就是:通过{@link Looper#setMessageLogging(Printer)}方法,监测Handler打印消息之间间隔的时间,
 * 来监控获取主线程执行持续时间,然后通过一个HandlerThread子线程来打印主线程堆栈信息.
 * {@link #receiveStartMessage()}方法会给WatchHandler发送一个开启监听的消息。
 * WatchHandler会每隔{@link #DUMP_STACK_DELAY_MILLIS}dump一次堆栈数据，
 * 此时如果{@link #receiveFinishMessage()}检测到没有超时，会通知WatchHandler取消继续dump
 * （这里会有较小概率发生线程安全问题，明明正常finishMessage，WatchHandler还是打印了堆栈，但是不会无限打印）。
 * 如果{@link #receiveFinishMessage()}没有收到消息，或者收到超时消息，则忽略消息，WatchHandler
 * 会自己处理超时打印（这里之所以这么做主要是避免线程同步）。
 *
 * @author xl
 * @version V1.2
 * @since 16/8/15
 */
public class BlockUtils {
    private static final String TAG = "BlockUtils";
    private static final String HANDLER_THREAD_TAG = "watch_handler_thread";
    /**
     * 用于分割字符串,LogCat对打印的字符串长度有限制
     */
    private static final String LINE_SEPARATOR = "3664113077962208511";

    /**
     * 卡顿,单位毫秒,这个参数因为子线程也要用,为了避免需要线程同步,所以就static final了,自定义请直接修改这个值.
     */
    private static final long BLOCK_DELAY_MILLIS = 800;
    /**
     * Dump堆栈数据时间间隔,单位毫秒
     */
    private static final long DUMP_STACK_DELAY_MILLIS = 160;
    private static final long START_DUMP_STACK_DELAY_MILLIS = 80;
    private static final long SYNC_DELAY = 100;

    /**
     * 起一个子线程,用来打印主线程的堆栈信息.因为是要监控主线程是否有卡顿的,所以主线程现在是无法打印堆栈的,
     * 所以需要起一个子线程来打印主线程的堆栈信息.
     */
    private HandlerThread mWatchThread;
    private Handler mWatchHandler;

    /**
     * 禁止外部实例化,请使用BlockUtils#getInstance方法
     */
    private BlockUtils() {
    }

    private Printer createBlockPrinter() {
        return new Printer() {
            /**
             * 纪录当前Printer回调的状态,注意这里初始状态必须是true.
             */
            private boolean mPrinterStart = true;

            @Override
            public void println(String s) {
                // 这里因为Looper.loop方法内会在Handler开始和结束调用这个方法,所以这里也对应两个状态,start和finish
                if (mPrinterStart) {
                    receiveStartMessage();
                } else {
                    receiveFinishMessage();
                }
                mPrinterStart = !mPrinterStart;
            }
        };
    }

    private PrintStaceInfoRunnable mPrintStaceInfoRunnable;

    private class PrintStaceInfoRunnable implements Runnable {

        long mStartMillis = System.currentTimeMillis();

        private StringBuilder mStackInfo = new StringBuilder();

        private boolean isTimeOut() {
            return System.currentTimeMillis() - mStartMillis > BLOCK_DELAY_MILLIS + SYNC_DELAY;
        }

        @Override
        public void run() {
            // 只要超时就打印堆栈数据，然后结束
            if (isTimeOut()) {
                printStackTraceInfo(mStackInfo.toString());
                mStackInfo.delete(0, mStackInfo.length());
                return;
            }

            // 没有超时则继续dump
            mWatchHandler.postDelayed(this, DUMP_STACK_DELAY_MILLIS);

            // dump堆栈
            Thread dumpThread = getDumpThread();
            StackTraceElement[] stackTraceElements = dumpThread.getStackTrace();
            // 注意这里仅仅是打印当前堆栈信息而已,实际代码不一定就是卡这里了.
            // 比如此次Handler一共要处理三个方法
            // method0(); 需要100ms
            // method1(); 需要200ms
            // method2(); 需要300ms
            // 其实最佳方案是这三个方法全部打印,但从代码层面很难知道是这三个方法时候打印
            // 只能每隔一段时间（比如：100ms）dump一次主线程堆栈信息,但是因为线程同步问题，可能第一个method0dump不到
            mStackInfo.append("\n").append(System.currentTimeMillis() - mStartMillis)
                    .append("ms时堆栈状态\n").append(LINE_SEPARATOR);
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                mStackInfo.append(stackTraceElement.toString()).append("\n");
            }
            mStackInfo.append(LINE_SEPARATOR);
        }

        @NonNull
        private Thread getDumpThread() {
            return Looper.getMainLooper().getThread();
        }

        private void printStackTraceInfo(String info) {
            String[] split = info.split(LINE_SEPARATOR);
            for (String s : split) {
                Log.d(TAG, s + "\n");
            }
        }
    }

    private long mStartTime;

    private void receiveStartMessage() {
        mStartTime = System.currentTimeMillis();
        // 注意当前类所有代码,除了这个方法里的代码,其他全部是在主线程执行.
        mPrintStaceInfoRunnable = new PrintStaceInfoRunnable();
        mWatchHandler.postDelayed(mPrintStaceInfoRunnable, START_DUMP_STACK_DELAY_MILLIS);
    }

    public void sendCrashMessage() {
        receiveFinishMessage();
    }

    private void receiveFinishMessage() {
        long end = System.currentTimeMillis();
        long delay = end - mStartTime;
        if (delay >= BLOCK_DELAY_MILLIS) {
            Log.w(TAG, "检测到超时，App执行本次Handler消息消耗了:" + delay + "ms\n");
        } else {
            mWatchHandler.removeCallbacks(mPrintStaceInfoRunnable);
        }
        mPrintStaceInfoRunnable = null;
    }

    public void start() {
        if (mWatchThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mWatchThread.quitSafely();
            } else {
                mWatchThread.quit();
            }
        }
        mWatchThread = new HandlerThread(HANDLER_THREAD_TAG);
        mWatchThread.start();
        mWatchHandler = new Handler(mWatchThread.getLooper());
        Looper.getMainLooper().setMessageLogging(getInstance().createBlockPrinter());
    }

    public void stop() {
        Looper.getMainLooper().setMessageLogging(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mWatchThread.quitSafely();
        } else {
            mWatchThread.quit();
        }
        mWatchThread = null;
        mWatchHandler = null;
    }

    public static BlockUtils getInstance() {
        return InstanceHolder.sInstance;
    }

    private static class InstanceHolder {
        private static final BlockUtils sInstance = new BlockUtils();
    }
}