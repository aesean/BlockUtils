package com.aesean.blockutils;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Printer;

/**
 * BlockUtils
 * BlockCanary已经非常好了,主要是BlockCanary是把堆栈信息打印到文件,感觉debug开发时候不是很方便.
 * 所以根据BlockCanary原理写了一个非常轻量的,把卡顿数据打印到LogCat的卡顿检测工具.
 * 原理大致就是:通过{@link Looper#setMessageLogging(Printer)}方法,通过Handler打印消息来监控获取主线程执行持续时间,
 * 然后通过一个HandlerThread线程来打印主线程堆栈信息.
 *
 * @author xl
 * @version V1.0
 * @since 16/8/15
 */
public class BlockUtils {
    private static final String TAG = "BlockUtils";
    private static final String HANDLER_THREAD_TAG = "block_handler_thread";

    /**
     * 卡顿,单位毫秒
     */
    private static final long BLOCK_DELAY_MILLIS = 400;
    /**
     * Dump堆栈数据时间间隔,单位毫秒
     */
    private static final long DUMP_STACK_DELAY_MILLIS = 100;
    /**
     * 纪录当前Printer回调的状态,注意这里初始状态必须是true.
     */
    private boolean mPrinterStart = true;

    /**
     * 起一个子线程,用来打印主线程的堆栈信息.因为是要监控主线程是否有卡顿的,所以主线程现在是无法打印堆栈的,
     * 所以需要起一个子线程来打印主线程的堆栈信息.
     */
    private HandlerThread mBlockThread;
    private Handler mBlockHandler;

    /**
     * 禁止外部实例化,请使用BlockUtils#getInstance方法
     */
    private BlockUtils() {
    }

    private Printer createBlockPrinter() {
        return new Printer() {
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

    private Runnable mPrintStaceInfoRunnable;

    private long mStartTime;

    protected void receiveStartMessage() {
        mStartTime = System.currentTimeMillis();
        // 注意当前类所有代码,除了这个方法里的代码,其他全部是在主线程执行.
        mPrintStaceInfoRunnable = new Runnable() {
            long mStartMillis = System.currentTimeMillis();

            private StringBuilder mStackInfo = new StringBuilder();
            private int mTimes = 0;

            @Override
            public void run() {
                mTimes++;
                long end = System.currentTimeMillis();
                Thread mainThread = Looper.getMainLooper().getThread();
                StackTraceElement[] stackTraceElements = mainThread.getStackTrace();
                // 注意这里仅仅是打印当前堆栈信息而已,实际代码不一定就是卡这里了.
                // 比如此次Handler一共要处理三个方法
                // method0(); 需要100ms
                // method1(); 需要200ms
                // method2(); 需要300ms
                // 其实最佳方案是这三个方法全部打印,但从代码层面很难知道是这三个方法时候打印
                // 这里实际这里是每100ms dump一次主线程堆栈信息,然后又因为线程同步问题,所以可能第一个method0就dump不到
                mStackInfo.append("\n").append(DUMP_STACK_DELAY_MILLIS * mTimes).append("ms").append("时堆栈状态");
                for (StackTraceElement stackTraceElement : stackTraceElements) {
                    mStackInfo.append(stackTraceElement.toString()).append("\n");
                }
                if (end - mStartMillis > BLOCK_DELAY_MILLIS) {
                    Log.e(TAG, "\n**************************卡顿时候的堆栈信息**************************");
                    printStackTraceInfo(mStackInfo.toString());
                    mStackInfo.delete(0, mStackInfo.length());
                }
                mBlockHandler.postDelayed(this, DUMP_STACK_DELAY_MILLIS);
            }
        };
        mBlockHandler.postDelayed(mPrintStaceInfoRunnable, DUMP_STACK_DELAY_MILLIS);
    }

    private static void printStackTraceInfo(String info) {
        Log.w(TAG, info);
    }

    protected void receiveFinishMessage() {
        long end = System.currentTimeMillis();
        long delay = end - mStartTime;
        if (delay >= BLOCK_DELAY_MILLIS) {
            Log.e(TAG, "兄弟你的App刚卡了:" + delay + "ms\n");
        }
        mBlockHandler.removeCallbacks(mPrintStaceInfoRunnable);
    }

    public void start() {
        mBlockThread = new HandlerThread(HANDLER_THREAD_TAG);
        mBlockThread.start();
        mBlockHandler = new Handler(mBlockThread.getLooper());
        Looper.getMainLooper().setMessageLogging(getInstance().createBlockPrinter());
    }

    public void stop() {
        Looper.getMainLooper().setMessageLogging(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBlockThread.quitSafely();
        } else {
            mBlockThread.quit();
        }
        mBlockThread = null;
        mBlockHandler = null;
    }

    public static BlockUtils getInstance() {
        return InstanceHolder.sInstance;
    }

    private static class InstanceHolder {
        private static BlockUtils sInstance = new BlockUtils();
    }
}