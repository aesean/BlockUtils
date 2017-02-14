# BlockUtils(Deprecated)
项目被转移到
https://github.com/aesean/ActivityStack
可以直接参考这里https://github.com/aesean/ActivityStack/blob/master/app/src/main/java/com/aesean/activitystack/utils/BlockUtils.java
当前项目不再进行更新。

一个帮助监测Android是否卡顿的，超轻量级工具类，主要是方便debug测试。

##使用方法
https://github.com/aesean/BlockUtils/blob/master/app/src/main/java/com/aesean/blockutils/BlockUtils.java

代码非常少，直接复制这个类到自己项目里就可以。然后在一个合适的地方(通常写在Application里)调用:
<pre><code>BlockUtils.getInstance().install();</code></pre>
即可。
##效果图

<img src="https://github.com/aesean/BlockUtils/blob/master/logcat_0.png" alt="GitHub" title="LogCat效果图0"/>
<img src="https://github.com/aesean/BlockUtils/blob/master/logcat_1.png" alt="GitHub" title="LogCat效果图1"/>
