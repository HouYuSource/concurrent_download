package cn.shaines.core.utils;

import cn.shaines.core.utils.HttpClient.Response.BodyHandlers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description 代理池工具
 * @date created in 2019-08-27 20:49:29
 * @author houyu for.houyu@foxmail.com
 */
public class ProxyPoolUtil {

    private static Logger logger = LoggerFactory.getLogger(ProxyPoolUtil.class);

    /** 存储所有可用的代理, 包括之前的可用的 */
    private Set<String> usefulSet = new HashSet<>(256);
    /** 存储代理的响应时间顺序的Map */
    private TreeMap<String, String> sortMap = new TreeMap<>(String::compareTo);
    /** 存储准备完毕可以获取的代理 */
    private LinkedList<String> readyProxyList;
    /** 定义某一时刻 */
    private long instant = 1566866991154L;
    /** 一个定长的集合 */
    private FixedCache<Long> lastTenTimes = new FixedCache<>(10);
    /** 存储捕获代理对象实现 */
    private List<CaptureProxy> captureProxyList = new ArrayList<>(8);
    /** 获取的次数 */
    private volatile LongAdder countAdder = new LongAdder();
    /** 任务线程池 */
    private ScheduledExecutorService monitorExecutorPool = new ScheduledThreadPoolExecutor(1);
    //
    /** 添加捕获代理对象 */
    public void addCaptureProxy(int index, CaptureProxy captureProxy) {
        captureProxyList.add(index, captureProxy);
    }

    /** 获取代理对象 */
    private List<String> findAllProxy() {
        List<String> allProxy = new ArrayList<>(this.captureProxyList.size() * 50);
        CountDownLatch countDownLatch = new CountDownLatch(this.captureProxyList.size());
        //
        for(CaptureProxy captureProxy : captureProxyList) {
            ThreadPoolUtil.get().submit(() -> {
                Collection<String> proxyCollection = captureProxy.parse();
                logger.debug("{} 获取到代理对象{}个, 分别是:{}", captureProxy.getClass(), proxyCollection.size(), proxyCollection);
                allProxy.addAll(proxyCollection);
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await(60, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            logger.warn(CoreUtil.getDetailMessage(e));
        }
        return allProxy;
    }

    /** 检出可用的代理对象 */
    private void checkUsefulSet(Collection<String> thisAllProxy) {
        this.usefulSet.addAll(thisAllProxy);
        logger.debug("本次新增代理数量:{}, 目前待检代理数量[去重]:{}", thisAllProxy.size(), this.usefulSet.size());
        this.sortMap.clear();
        List<String> removeList = new ArrayList<>(Double.valueOf(thisAllProxy.size() * 0.7).intValue());
        CountDownLatch countDownLatch = new CountDownLatch(this.usefulSet.size());
        for(String proxy : this.usefulSet) {
            String[] host_port = proxy.split(":", 2);
            ThreadPoolUtil.get().submit(() -> {
                final long start = System.currentTimeMillis();
                ThreadLocal<Long> threadLocal = ThreadLocal.withInitial(() -> start);
                try {
                    HttpClient.buildHttpClient().buildRequest("https://www.sogou.com/").setTimeout(3000).execute(null);
                    long timeMillis = System.currentTimeMillis();
                    logger.debug("\t[    成功]:{}, \t\t耗时:{}, 执行线程:{}", proxy, timeMillis - threadLocal.get(), Thread.currentThread().getName());
                    this.sortMap.put(new StringBuilder(128).append(timeMillis - threadLocal.get()).append("_").append(instant - timeMillis).toString(), proxy);
                } catch(Exception e) {
                    logger.debug("\t[失败    ]:{}, \t\t耗时:{}, 执行线程:{}", proxy, System.currentTimeMillis() - threadLocal.get(), Thread.currentThread().getName());
                    removeList.add(proxy);
                } finally {
                    threadLocal.remove();
                    countDownLatch.countDown();
                }
            });
        }
        try {
            countDownLatch.await(60, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            logger.warn(CoreUtil.getDetailMessage(e));
        }
        logger.debug("检查代理对象{}个, 不可用代理有{}个", this.usefulSet.size(), removeList.size());
        this.usefulSet.removeAll(removeList);
        this.readyProxyList = new LinkedList<>(this.sortMap.values());
        logger.debug("目前可用代理有{}个, 分别是:{}", this.usefulSet.size(), this.readyProxyList);
    }

    /** 初始化数据 */
    private void init() {
        List<String> allProxy = findAllProxy();
        checkUsefulSet(allProxy);
    }

    /** 执行方法 */
    public void run() {
        if(this.countAdder.longValue() == 0) {
            logger.info("初始化代理数据");
            monitorExecutorPool.scheduleAtFixedRate(this::init, 0, 10, TimeUnit.SECONDS);
        }
    }

    /** 关闭, 释放资源 */
    public void close() {
        this.monitorExecutorPool.shutdown();
    }

    /** 返回代理对象 */
    public String getProxy() {
        this.lastTenTimes.add(System.currentTimeMillis());
        this.countAdder.increment();
        if(this.readyProxyList == null) {
            return null;
        }
        if(this.readyProxyList.size() < 10) {
            // 少于10, 去获取抓取新的数据
            logger.debug("代理池可用数量少于10, 尝试去抓取新的代理入池");
            this.init();
        }
        String returnProxy = handleReturnProxy();
        logger.debug("返回代理:{}", returnProxy);
        return returnProxy;
    }

    /** 处理并且返回代理 */
    private String handleReturnProxy() {
        String returnProxy = null;
        //
        boolean fastFlag = (this.lastTenTimes.getFirst() - this.lastTenTimes.getLast()) / 10 < 1000;// 最近十次中平均小于1秒, 获取 快
        if(fastFlag || this.readyProxyList.size() < 10) {
            // fastFlag 或者 可用的数量小于10
            logger.debug("达到重复使用代理的条件");
            if(this.readyProxyList.size() == 0) {
                logger.debug("准备就绪的代理已经使用完毕, 正在使用上一次测试完毕的代理");
                this.readyProxyList = new LinkedList<>(this.sortMap.values());
            }
            if(this.readyProxyList.size() > 0) {
                if(this.countAdder.longValue() % 10 == 0) {
                    // 每一个代理重复使用10次
                    returnProxy = this.readyProxyList.remove(0);
                } else {
                    returnProxy = this.readyProxyList.get(0);
                }
            }
        } else {
            returnProxy = this.readyProxyList.remove(0);
        }
        return returnProxy;
    }

    private ProxyPoolUtil(){}
    private interface SingletonHolder{
        ProxyPoolUtil INSTANCE = new ProxyPoolUtil();
    }
    public static ProxyPoolUtil get() {
        return SingletonHolder.INSTANCE;
    }

    /** 自己实现一个简单的定长队列 */
    private static class FixedCache<E>{
        private List<E> dataList;
        private volatile int size;
        public FixedCache(int size){
            this.dataList  = new LinkedList<>();
            this.size = size;
        }
        public void add(E e){
            dataList.add(0, e);
            if (dataList.size() > this.size){
                dataList.remove(this.size);
            }
        }
        public void addAll(Collection<? extends E> es){
            for (E e : es){
                add(e);
            }
        }
        public List<E> toList(){
            return dataList;
        }
        public E getFirst() {
            return this.dataList.get(0);
        }
        public E getLast() {
            return this.dataList.get(this.dataList.size() - 1);
        }
    }

    /** 捕获代理接口, 如果有其他实现, 直接实现该接口, 然后 */
    public interface CaptureProxy{
        /**
         * 解析信息返回代理
         * @return ["123.21.152.12:1020", "50.102.52.2:5620"]
         */
        Collection<String> parse();
    }

    public static class CaptureProxyImpl1 implements CaptureProxy{
        HttpClient httpClient = HttpClient.buildHttpClient();
        @Override
        public Collection<String> parse() {

            List<String> proxyList = new ArrayList<>(128);
            for (int i = 1; i < 5; i++){
                String html = httpClient.buildRequest("http://ip.jiangxianli.com/?page=" + i).execute(BodyHandlers.ofString()).getBody();
                List<String> list = CoreUtil.regexMatcher(html, "data-url=\"http://(.*)\"\\s*data-unique-id=", List.class);
                if (list.size() == 0){
                    break;
                }
                proxyList.addAll(list);
            }
            return proxyList;
        }
    }

}

/*
使用方式:
ProxyPoolUtil proxyPoolUtil = ProxyPoolUtil.get();
proxyPoolUtil.addCaptureProxy(0, new CaptureProxyImpl1());
proxyPoolUtil.run();
//
String proxy = proxyPoolUtil.getProxy();
System.out.println("proxy = " + proxy);

proxyPoolUtil.close();
 */
