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
 * 原理大致就是:通过{@link Looper#setMessageLogging(Printer)}方法,监测Handler打印消息之间间隔的时间,
 * 来监控获取主线程执行持续时间,然后通过一个HandlerThread子线程来打印主线程堆栈信息.
 *
 * @author xl
 * @version V1.1
 * @since 16/8/15
 */
public class BlockUtils {
    private static final String TAG = "BlockUtils";
    private static final String HANDLER_THREAD_TAG = "block_handler_thread";
    /**
     * 用于分割字符串,LogCat对打印的字符串长度有限制
     */
    private static final String LINE_SEPARATOR = "3664113077962208511";

    /**
     * 卡顿,单位毫秒,这个参数因为子线程也要用,为了避免需要线程同步,所以就static final了,自定义请直接修改这个值.
     */
    private static final long BLOCK_DELAY_MILLIS = 400;
    /**
     * Dump堆栈数据时间间隔,单位毫秒
     */
    private static final long DUMP_STACK_DELAY_MILLIS = 100;

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

        boolean mNeedStopPostDelayed = false;
        long mStartMillis = System.currentTimeMillis();

        private StringBuilder mStackInfo = new StringBuilder();
        private int mTimes = 0;

        public void setNeedStopPostDelayed() {
            // 这里不需要线程同步,不同步顶多也就多打印些堆栈信息,不会导致无限post
            mNeedStopPostDelayed = true;
        }

        @Override
        public void run() {
            if (mNeedStopPostDelayed) {
                return;
            }
            // 这里会低概率出现线程安全问题.receiveFinishMessage里removeCallbacks移除this的时候
            // 跟这里的postDelayed可能有线程同步问题,removeCallbacks先移除了,然后这里的代码已经被执行了,导致this被无限循环post
            // 如果LogCat出现无限打印就杀死App重新打开,这里是有小概率出现问题.
            // 线程同步会影响性能,而且也没多大必要,这里就不同步数据了.
            mBlockHandler.postDelayed(this, DUMP_STACK_DELAY_MILLIS);
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
            mStackInfo.append("\n").append(DUMP_STACK_DELAY_MILLIS * mTimes)
                    .append("ms").append("时堆栈状态\n").append(LINE_SEPARATOR);
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                mStackInfo.append(stackTraceElement.toString()).append("\n");
            }
            mStackInfo.append(LINE_SEPARATOR);
            if (end - mStartMillis > BLOCK_DELAY_MILLIS) {
                Log.e(TAG, "**************************堆栈信息**************************");
                printStackTraceInfo(mStackInfo.toString());
                mStackInfo.delete(0, mStackInfo.length());
            }
        }

        private void printStackTraceInfo(String info) {
            String[] split = info.split(LINE_SEPARATOR);
            for (String s : split) {
                Log.w(TAG, s + "\n");
            }
        }
    }

    private long mStartTime;

    private void receiveStartMessage() {
        mStartTime = System.currentTimeMillis();
        // 注意当前类所有代码,除了这个方法里的代码,其他全部是在主线程执行.
        mPrintStaceInfoRunnable = new PrintStaceInfoRunnable();
        mBlockHandler.postDelayed(mPrintStaceInfoRunnable, DUMP_STACK_DELAY_MILLIS);
    }

    private void receiveFinishMessage() {
        long end = System.currentTimeMillis();
        long delay = end - mStartTime;
        if (delay >= BLOCK_DELAY_MILLIS) {
            Log.e(TAG, "App执行以上方法消耗了:" + delay + "ms\n");
        }
        mPrintStaceInfoRunnable.setNeedStopPostDelayed();
        mBlockHandler.removeCallbacks(mPrintStaceInfoRunnable);
    }

    public void start() {
        if (mBlockThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBlockThread.quitSafely();
            } else {
                mBlockThread.quit();
            }
        }
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
        private static final BlockUtils sInstance = new BlockUtils();
    }
}