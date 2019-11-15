package cn.shaines.core.utils;

import cn.shaines.core.utils.HttpClient.Response;
import cn.shaines.core.utils.HttpClient.Response.BodyHandlers;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
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
        Long totalLength = getContentLength();
        if(totalLength == null) {
            insertMessage("获取文件的长度失败");
            throw new RuntimeException("获取文件的长度失败");
        }
        total = totalLength;
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
    private Long getContentLength() {
        Response<Long> response = httpClient.buildRequest(this.builder.url).GET().execute((request, http) -> {
            if(http.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return http.getContentLengthLong();
            }
            return null;
        });
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
        current = new AtomicLong(total - this.paces.stream().map(v -> v.endIndex - v.startIndex + 1).reduce(0L, (a, b) -> a + b) + 1);
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
        useTime = useTime <= 0 ? 1000 : useTime;
        return (this.total / 1000 / useTime / 1000) + "KB/s";
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