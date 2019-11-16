> Java并发编程：03-多线程并发下载器, 支持断点下载(手写不限速的迷你版迅雷)
> 主要是最近学习完一些初级的并发知识, 所以想使用这些知识做一个小小工具, 巩固一下知识点, 然后就想到了多线程并发下载文件的这个小工具, 这个工具可以媲美迅雷中的下载速度哦~~, 我尝试下载过11M/s的速度, 这个速度其实还和你的带宽以及下载的资源有关, 所以在此不做太多关于速度上的较真...

使用到的知识点有如下:
* 01 随机访问文件: RandomAccessFile
* 02 Http 协议的 请求头 的 Range 分段请求资源
* 03 Http网络请求: HttpURLConnection 使用(基于 HttpURLConnection 封装的 HttpClient)
* 04 线程池的使用: ThreadPoolExecutor / ScheduledThreadPoolExecutor
* 05 守护线程的使用
* 06 原子增长类: AtomicLong
* 07 计时器的使用CountDownLatch
* 08 并发量的控制(信号量): Semaphore
* 09 阻塞队列的使用: LinkedBlockingQueue
* 10 自定义下载回调接口
* 11 Builer模式的构建

## RandomAccessFile简单介绍
RandomAccessFile适用于由大小已知的记录组成的文件，所以我们可以使用seek()将记录从一处转移到另一处，然后读取或修改记录。

随机访问文件的行为类似存储在文件系统中的一个大型 byte 数组。存在指向该隐含数组的光标或索引，称为文件指针；输入操作从文件指针开始读取字节，并随着对字节的读取而前移此文件指针。如果随机访问文件以读取/写入模式创建，则输出操作也可用；输出操作从文件指针开始写入字节，并随着对字节的写入而前移此文件指针。写入隐含数组的当前末尾之后的输出操作导致该数组扩展。该文件指针可以通过 getFilePointer 方法读取，并通过 seek 方法设置。

**构造方法:**

```java
public RandomAccessFile(File file,  String mode) throws FileNotFoundException
```
**mode类型:**

* r——以只读方式打开。调用结果对象的任何 write 方法都将导致抛出 IOException。
* rw——打开以便读取和写入。如果该文件尚不存在，则尝试创建该文件。
* rws—— 打开以便读取和写入，对于 “rw”，还要求对文件的内容或元数据的每个更新都同步写入到底层存储设备。
* rwd——打开以便读取和写入，对于 “rw”，还要求对文件内容的每个更新都同步写入到底层存储设备。

**文件偏移量:**
置到此文件开头测量到的文件指针偏移量，在该位置发生下一个读取或写入操作。

```java
public native void seek(long pos) throws IOException
```

**RandomAccessFile小结:**
> RandomAccessFile类特殊之处在于支持搜寻方法，并且只适用于文件，这种随机访问特性，为多线程下载提供了文件分段写的支持。
> 需要注意的是，在RandomAccessFile的大多函数均是native的，在JDK1.4之后，RandomAccessFile大多数功能由nio存储映射文件所取代。所谓存储映射文件，简单来说 是由一个文件到一块内存的映射。内存映射文件与虚拟内存有些类似，通过内存映射文件可以保留一个地址空间的区域，同时将物理存储器提交给此区域，内存文件映射的物理存储器来自一个已经存在于磁盘上的文件，而且在对该文件进行操作之前必须首先对文件进行映射。使用内存映射文件处理存储于磁盘上的文件时，将不必再对文件执行I/O操作，使得内存映射文件在处理大数据量的文件时能起到相当重要的作用。有了内存映射文件，我们就可以假定整个文件都放在内存中，而且可以完全把它当做非常大的数组来访问。

## Range范围请求
Range，是在 HTTP/1.1里新增的一个 header field，它允许客户端实际上只请求文档的一部分，或者说某个范围。

有了范围请求，HTTP 客户端可以通过请求曾获取失败的实体的一个范围（或者说一部分），来恢复下载该实体。当然这有一个前提，那就是从客户端上一次请求该实体到这次发出范围请求的时段内，该对象没有改变过。例如：

```
GET /bigfile.html HTTP/1.1
Host: www.joes-hardware.com
Range: bytes=0-1000
```

**Range头域使用形式如下**

```
表示头500个字节：bytes=0-499  
表示第二个500字节：bytes=500-999  
表示最后500个字节：bytes=-500  
表示500字节以后的范围：bytes=500-  
第一个和最后一个字节：bytes=0-0,-1 
```

> 如果客户端发送的请求中Range这个值存在而且有效，则服务端只发回请求的那部分文件内容，响应的状态码变成206，表示Partial Content，并设置Content-Range。如果无效，则返回416状态码，表明Request Range Not Satisfiable如果不包含Range的请求头，则继续通过常规的方式响应。
> 比如某文件的大小是 1000 字节，client 请求这个文件时用了 Range: bytes=0-500，那么 server 应该把这个文件开头的 501 个字节发回给 client，同时回应头要有如下内容：Content-Range: bytes 0-500/1000,并返回206状态码。
> 并不是所有服务器都接受范围请求，但很多服务器可以。服务器可以通过在响应中包含 Accept-Ranges 首部的形式向客户端说明可以接受的范围请求。这个首部的值是计算范围的单位，通常是以字节计算的。

## Http网络请求
请参考我封装的一个[基于HttpURLConnection封装超级好用的HttpClient](https://blog.csdn.net/JinglongSource/article/details/102559449)
> 基于HttpURLConnection封装超级好用的HttpClient,模仿jdk11中的HttpClient封装
> https://blog.csdn.net/JinglongSource/article/details/102559449

## 看看迅雷是如何做的
比如说: 下载最近的一部电影: 少年的你, 看看迅雷是如何下载的呢

**步骤01: 首先获取到资源的总长度**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116174613882.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0ppbmdsb25nU291cmNl,size_16,color_FFFFFF,t_70)

**步骤02: 平分资源, 设置线程分段请求资源**

![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116175152885.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0ppbmdsb25nU291cmNl,size_16,color_FFFFFF,t_70)
**查看下载目录中的文件**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116175522996.png)
### 在了解到这些东西之后, 我们就可以开始入手代码啦!!
简要实现步骤:
1. 首次请求资源, 获取长度
2. 创建一个同等长度的文件和用于断点下载的文件
3. 根据线程数量, 每个线程一次请求的资源长度, 分配好**请求步伐**
4. 设置计数器, 等待执行完成再退出程序
5. ...


**获取长度**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116180829957.png)

**创建文件**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116181404423.png)


**计算步伐**![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116181211488.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0ppbmdsb25nU291cmNl,size_16,color_FFFFFF,t_70)

**下载过程的结果回调**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116181659297.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L0ppbmdsb25nU291cmNl,size_16,color_FFFFFF,t_70)


**最终结果**
![在这里插入图片描述](https://shaines.cn/view/image?src=https://img-blog.csdnimg.cn/20191116181545390.png)

### 最终源码
> 笔记都写在源码处, 以及如何使用的介绍等...

```java
import cn.shaines.core.utils.HttpClient.Response;
import cn.shaines.core.utils.HttpClient.Response.BodyHandlers;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习:
 * https://blog.csdn.net/qq_34401512/article/details/77867576
 *
 * HttpClient 请查看我的博客:
 * https://blog.csdn.net/JinglongSource/article/details/102559449
 *
 * 简单使用:
 * ConcurrentDownLoad
 *         .builder()
 *         // 设置URL
 *         .setUrl("http://117.148.175.41/cdn/pcfile/20190904/16/58/GeePlayerSetup_app.exe")
 *         // 设置线程每次请求的块大小 (5M)
 *         .setBlockSize(1024L * 5)
 *         // 设置线程数量
 *         .setThreadCount(5)
 *         // 设置保存路径
 *         .setPath("C:\\Users\\houyu\\Desktop\\GeePlayerSetup_app.exe")
 *         // 设置存在是否删除(如果设置 setKeepOnIfDisconnect(true) 则失效)
 *         .setDeleteIfExist(true)
 *         // 是否支持断点下载
 *         .setKeepOnIfDisconnect(true)
 *         // 创建
 *         .build()
 *         // 开始
 *         .start((msg, total, current, speed) -> {
 *             // log.debug("msg:{} total:{} current:{} speed:{} KB/s, 已下载进度:{}", msg, total, current, speed / 1024, total == 0 ? 0 : (float) current / (float) total);
 *         });
 *
 * @description 并发下载文件工具
 * @date 2019-11-12 14:35:26
 * @author houyu for.houyu@foxmail.com
 */
public class ConcurrentDownLoad {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentDownLoad.class);

    private Builder builder;
    /** HttpClient */
    private HttpClient httpClient;
    /** 任务线程池 */
    private ThreadPoolExecutor taskExecutor;
    /** 定时执行器 */
    private ScheduledThreadPoolExecutor scheduledExecutor;
    /** 信号量 */
    private Semaphore semaphore;
    /** 计数器 */
    private CountDownLatch countDownLatch;
    /** 开始时间 */
    private volatile long startTime = 0;
    /** 总长度 */
    private long total = 0;
    /** 当前的进度 */
    private AtomicLong current;
    /** 最后一次的索引位置 */
    private volatile long lastIndex = 0;
    /** 速度 */
    private volatile long speed = 0;
    /** 断点下载文件路径 */
    private Path keepOnPath;
    /** 任务步伐 (CopyOnWriteArrayList) */
    private List<Pace> paces;
    /** 回调信息队列 */
    private LinkedBlockingQueue<String> callbackQueue;
    /** 回调方法 */
    private Callback callback;


    public static Builder builder() {
        return new Builder();
    }

    private ConcurrentDownLoad(Builder builder) {
        this.builder = builder;
        this.httpClient = HttpClient.buildHttpClient();
        this.taskExecutor = new ThreadPoolExecutor(builder.threadCount, builder.threadCount, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new AbortPolicy());
        this.scheduledExecutor = new ScheduledThreadPoolExecutor(1);
        this.semaphore = new Semaphore(this.builder.threadCount);
        this.current = new AtomicLong(0);
        //
        this.keepOnPath = Paths.get(builder.path + ".conf");
        this.callbackQueue = new LinkedBlockingQueue<>();
    }

    public void start() {
        this.start((msg, total, current, speed) -> {});
    }

    public void start(Callback c) {
        try {
            // 保留两位小数
            // DecimalFormat format = new DecimalFormat("0.00");
            // 保留百分比
            DecimalFormat format = new DecimalFormat("0.00%");
            this.run((msg, total, current, speed) -> {
                // 记录日志
                if(log.isDebugEnabled()) {
                    log.debug("msg:{} total:{} current:{} speed:{} KB/s, 已下载进度:{}", msg, total, current, speed / 1024, format.format(total == 0 ? 0 : (float) current / (float) total));
                }
                // 回调使用者
                c.accept(msg, total, current, speed);
            });
        } catch(Exception e) {
            log.warn("start(Callback call) has error", e);
            c.accept(e.getMessage(), total, current.get(), 0);
        }
    }

    private void run(Callback c) throws Exception {
        this.callback = c;
        // 运行回调任务
        runCallbackTask();
        // 初始化参数以及校验
        initArgs();
        // 开启监控任务
        runMonitor();
        // 开启步伐任务(同步代码,阻塞)
        runPaceTaskSync();
        // 结束收尾工作
        doFinish();
    }

    /**
     * 运行回调任务
     */
    private void runCallbackTask() {
        Thread callbackThread = new Thread(() -> {
            while(true) {
                try {
                    // 使用阻塞方法 take()
                    String msg = callbackQueue.take();
                    callback.accept(msg, total, current.get(), speed);
                } catch(InterruptedException e) {
                    callback.accept(e.getMessage(), total, current.get(), 0);
                }
            }
        });
        // 设置守护进程, 主线程结束, 那就销毁即可
        callbackThread.setName("callbackThread");
        callbackThread.setDaemon(true);
        callbackThread.start();
    }


    /**
     * 初始化参数
     */
    private void initArgs() throws IOException {
        // 先初始化一下开始时间, 如果找到断点文件的话,有可能会被覆盖
        startTime = System.currentTimeMillis();
        insertMessage("start...");
        long totalLength = -1;
        for(int i = 1; i <= 5; i++) {
            // 尝试5次获取长度
            totalLength = getContentLength();
            if(totalLength > 0) {
                break;
            }
            insertMessage(String.format("获取长度失败: 正在重试:%s次", i));
        }
        if(totalLength <= 0) {
            insertMessage("获取文件的长度失败");
            throw new RuntimeException("获取文件的长度失败");
        }
        total = totalLength;
        insertMessage("请求资源:" + this.builder.url);
        insertMessage(String.format("文件总长度:%s字节(B)", total));
        // 重置 blockSize
        this.builder.setBlockSize(this.builder.blockSize >= total ? total : this.builder.blockSize);
        // 初始化文件信息
        initFileOrHandleKeepOn();
        if(this.paces == null || this.paces.isEmpty()) {
            initPaces();
        }
        writeKeepOnFile();
    }

    /**
     * 添加回调信息
     */
    private void insertMessage(String msg) {
        try {
            callbackQueue.put(msg);
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取文件长度
     */
    private long getContentLength() {
        Response<Long> response = httpClient.buildRequest(this.builder.url).GET()
                // 执行请求
                .execute((request, http) -> http.getContentLengthLong());
        return response.getBody();
    }

    /**
     * 初始化文件
     */
    private void initFileOrHandleKeepOn() throws IOException {
        File targetFile = new File(this.builder.path);
        if(targetFile.exists()) {
            // 文件存在
            insertMessage("文件存在");
            if(builder.keepOnIfDisconnect && keepOnPath.toFile().exists()) {
                // 支持断点
                insertMessage("初始化断点参数");
                try {
                    initKeepOnArgs();
                    insertMessage("完成解析断点参数");
                } catch(Exception e) {
                    insertMessage("解析断点参数失败: " + e.getMessage());
                }
            } else {
                if(builder.deleteIfExist) {
                    Files.deleteIfExists(targetFile.toPath());
                    insertMessage("删除文件");
                    createFile();
                }
            }
        } else {
            // 文件不存在
            insertMessage("文件不存在, 创建目录");
            Files.createDirectories(targetFile.getParentFile().toPath());
            createFile();
        }
    }

    /**
     * 初始化断点文件参数
     */
    private void initKeepOnArgs() throws IOException, NumberFormatException {
        /*
         * 定义约束,
         * 第一行是开始时间
         * 第二行至尾部都是未下载的步伐
         */
        List<String> lines = Files.readAllLines(this.keepOnPath, StandardCharsets.UTF_8);
        startTime = Long.parseLong(lines.remove(0));
        paces = new CopyOnWriteArrayList<>();
        for(String line : lines) {
            if(line.contains("-")) {
                String[] split = line.split("-", 2);
                paces.add(new Pace(Long.valueOf(split[0]), Long.valueOf(split[1])));
            }
        }
    }

    /**
     * 创建文件
     */
    private void createFile() throws IOException {
        // 使用 try-with-resource 实现 auto close
        try(RandomAccessFile raf = new RandomAccessFile(this.builder.path, "rwd")) {
            // 指定创建的文件的长度
            raf.setLength(total);
        }
    }

    /**
     * 初始化步伐
     */
    private void initPaces() {
        paces = new CopyOnWriteArrayList<>();
        long currentLength = total;
        long startIndex = 0;
        long endIndex;
        while(currentLength > 0) {
            long size = currentLength >= this.builder.blockSize ? this.builder.blockSize : currentLength;
            endIndex = startIndex + size;
            endIndex = endIndex >= total ? total : endIndex;
            paces.add(new Pace(startIndex, endIndex));
            currentLength = currentLength - size;
            startIndex = endIndex + 1;
        }
    }

    /**
     * 运行监控
     */
    private void runMonitor() {
        /*
         * 使用定时任务更新文件可能不是很准确, 但是影响不大, 没必要在回调的时候执行刷新, 因为磁盘IO很慢, 会影响下载效率
         * 可能不是最新的, 但是我们使用 RandomAccessFile 的时候也就是覆盖之前的一点, 实际结果是没有影响的
         */
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                speed = current.get() - lastIndex;
                lastIndex = current.get();
                writeKeepOnFile();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 写入断点文件
     */
    private void writeKeepOnFile() throws IOException {
        StringBuilder builder = new StringBuilder(1024);
        builder.append(startTime).append("\r\n");
        this.paces.forEach(v -> builder.append(v.startIndex).append("-").append(v.endIndex).append("\r\n"));
        Files.writeString(keepOnPath, builder.toString());
    }

    /**
     * 执行任务
     */
    private void runPaceTaskSync() {
        // 已完成(当前位置) = 总量 - 未完成 + 1
        current = new AtomicLong(total - this.paces.stream().map(v -> v.endIndex - v.startIndex + 1).reduce(0L, Long::sum) + 1);
        // 创建并发工具
        countDownLatch = new CountDownLatch(paces.size());
        for(Pace pace : paces) {
            insertMessage(String.format("pace:%s - %s", pace.startIndex, pace.endIndex));
            taskExecutor.submit(new DownLoadThread(pace));
        }
        // 等待结束
        try {
            countDownLatch.await();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        if(!this.paces.isEmpty()) {
            // 不为空, 说明中途有失败的, 递归执行任务
            runPaceTaskSync();
        }
    }

    /**
     * 处理完善工作
     */
    private void doFinish() throws IOException {
        insertMessage(String.format("用时: %s, 平均速度: %s, 存储路径: %s, 资源链接:%s", getUseTime(), getAverageSpeed(), this.builder.path, this.builder.url));
        taskExecutor.shutdown();
        scheduledExecutor.shutdown();
        // 删除断点下载日志文件
        Files.deleteIfExists(keepOnPath);
    }

    /**
     * 获取使用时长
     */
    private String getUseTime() {
        long endTime = System.currentTimeMillis();
        long userTime = endTime - startTime;
        long useMinute = userTime / 1000 / 60;
        long remainderSeconds = (userTime - (useMinute * 1000 * 60)) / 1000;
        return String.format("%s分%s秒", useMinute, remainderSeconds);
    }

    /**
     * 平均速度(KB)
     */
    private String getAverageSpeed() {
        long useTime = System.currentTimeMillis() - startTime;
        useTime = useTime > 0 ? useTime / 1000 : 1;
        return (this.total / 1000 / useTime) + "KB/s";
    }

    /**
     * 内部类用于实现下载并组装
     */
    private class DownLoadThread implements Runnable {
        private Pace pace;

        private DownLoadThread(Pace pace) {
            this.pace = pace;
        }

        @Override
        public void run() {
            try (RandomAccessFile file = new RandomAccessFile(builder.path, "rwd")) {
                semaphore.acquire();
                file.seek(pace.startIndex);
                httpClient.buildRequest(builder.url)
                        // 添加请求头
                        .addHeader("Range", "bytes=" + pace.startIndex + "-" + pace.endIndex)
                        // 执行请求
                        // .execute(BodyHandlers.ofCallbackByteArray(file::write))
                        .execute(BodyHandlers.ofCallbackByteArray((data, index, length) -> {
                            file.write(data, index, length);
                            current.addAndGet(length);
                            insertMessage("download...");
                        }));
                // 删除已经完成的步伐
                paces.remove(this.pace);
            } catch(Exception e) {
                // 如果这里报错, 有可能时候 RandomAccessFile 被占用, 也有可能是网络请求出现问题了, 再或者其他问题, 但是都不影响总体的, 如果失败了, 还会重试下载的
                // e.printStackTrace();
                insertMessage(String.format("%s-%s处理失败, 已进行添加队列稍后重新下载, 错误信息:%s", pace.startIndex, pace.endIndex, e.getMessage()));
            } finally {
                countDownLatch.countDown();
                semaphore.release();
            }
        }
    }

    public static class Builder {
        /** 同时下载的线程数 推荐(1-10) */
        private int threadCount = 5;
        /** 每个线程每次执行的文件大小(1M-10M) */
        private long blockSize = 1024;
        /** 服务器请求路径 */
        private String url;
        /** 本地路径 */
        private String path;
        /** 存在是否删除 */
        private boolean deleteIfExist = false;
        /** 是否断点续传 */
        private boolean keepOnIfDisconnect = true;

        public Builder setThreadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }
        public Builder setBlockSize(long blockSizeOfKb) {
            this.blockSize = blockSizeOfKb;
            return this;
        }
        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }
        public Builder setPath(String path) {
            this.path = path;
            return this;
        }
        public Builder setDeleteIfExist(boolean deleteIfExist) {
            this.deleteIfExist = deleteIfExist;
            return this;
        }
        public Builder setKeepOnIfDisconnect(boolean keepOnIfDisconnect) {
            this.keepOnIfDisconnect = keepOnIfDisconnect;
            return this;
        }

        public ConcurrentDownLoad build() {
            this.blockSize = this.blockSize * 1024;
            return new ConcurrentDownLoad(this);
        }
    }

    /**
     * 步伐对象
     */
    private static class Pace {
        private long startIndex;
        private long endIndex;

        private Pace(long startIndex, long endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            Pace pace = (Pace) o;
            return startIndex == pace.startIndex && endIndex == pace.endIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startIndex, endIndex);
        }
    }

    /**
     * 回调接口
     */
    public interface Callback {
        /**
         * 回调方法
         * @param msg 消息
         * @param total 总量
         * @param current 当前量
         * @param speed 速度(KB/s)
         */
        void accept(String msg, long total, long current, long speed);
    }

}

```

**如何使用?**
> 用于比较喜欢链式编程, 因此使用 builder 模式进行构建

```java
ConcurrentDownLoad.builder()
                // 设置URL
                .setUrl("http://117.148.175.41/cdn/pcfile/20190904/16/58/GeePlayerSetup_app.exe")
                // 设置线程每次请求的大小(1M)
                .setBlockSize(1024)
                // 设置线程数量
                .setThreadCount(5)
                // 设置保存路径
                .setPath("C:\\Users\\houyu\\Desktop\\GeePlayerSetup_app.exe")
                // 设置存在是否删除(如果设置 setKeepOnIfDisconnect(true) 则失效)
                .setDeleteIfExist(true)
                // 是否支持断点下载
                .setKeepOnIfDisconnect(true)
                // 创建
                .build()
                // 开始
                .start((msg, total, current, speed) -> {
                    // 下载回调方法
                    // log.debug("msg:{} total:{} current:{} speed:{} KB/s, 已下载进度:{}", msg, total, current, speed / 1024, format.format(total == 0 ? 0 : (float) current / (float) total));
                });
```

最后的话
* 参考博客 https://blog.csdn.net/qq_34401512/article/details/77867576
* 代码提交到github: https://github.com/HouYuSource/concurrent_download
* 如果我不是有一个迅雷5这神器(任何资源都不限制), 我真的有可能会入坑用java写一个GUI界面结合起来
* 无界面的使用: 在github中在有一个 **解压即用.rar** , 下载来之后, 在download.txt中的配置下载的资源, 然后双击start.bat就可以下载了资源了, 但是不知道为啥有些下载不了, 在IDE又可以, 不知道是什么问题引起...