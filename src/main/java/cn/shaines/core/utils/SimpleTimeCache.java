package cn.shaines.core.utils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @description: 简单定时缓存
 * @author: houyu
 * @create: 2018-12-05 00:02
 *
 * 使用:
 *         SimpleTimeCache<String, Object> build = SimpleTimeCache.<String, Object>builder().setOverTime(2000).build();
 *         build.put("A1", "A1");
 *         build.put("A2", "A2");
 *         Thread.sleep(3000);
 *
 *         System.out.println("build.get(\"A1\") = " + build.get("A1"));
 *         System.out.println("build.keySet() = " + build.keySet());
 *         System.out.println("build.entrySet() = " + build.entrySet());
 *         build.forEach((k, v) -> System.out.println(k + "=>" + v));
 *         System.out.println("build.containsKey(\"A1\") = " + build.containsKey("A1"));
 *         build.put("A3", "A3");
 *
 *         System.out.println("build.containsKey(\"A3\") = " + build.containsKey("A3"));
 *         build.remove("A3");
 *         System.out.println("build.containsKey(\"A3\") = " + build.containsKey("A3"));
 */
public class SimpleTimeCache<K, V> {

    private Builder builder;

    public static class Builder<K, V> {
        /** 阈值 */
        private int thresholdSize = 500;
        /** 过时 (10秒) */
        private int overTime = 10 * 1000;

        public Builder<K, V> setThresholdSize(int thresholdSize) {
            this.thresholdSize = thresholdSize;
            return this;
        }
        public Builder<K, V> setOverTime(int millis) {
            this.overTime = millis;
            return this;
        }

        public SimpleTimeCache<K, V> build() {
            return new SimpleTimeCache<K, V>(this);
        }
        // public <K, V> SimpleTimeCache<K, V> build(Class<K> keyClass, Class<V> valueClass) {
        //     return new SimpleTimeCache<K, V>(this);
        // }
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<K, V>();
    }

    /** 最后一个添加的时间 */
    private volatile long lastTime;
    /** 时间Map */
    private Map<K, Long> timeMap;
    /** 存储值得Map */
    private Map<K, V> valueMap;

    protected SimpleTimeCache(Builder builder) {
        this.builder = builder;
        valueMap = new ConcurrentHashMap<>(this.builder.thresholdSize);
        timeMap = new ConcurrentHashMap<>(this.builder.thresholdSize);
    }

    /** 检查并清除过时数据 */
    private void checkAndCleanStaleData() {
        if(System.currentTimeMillis() - lastTime > builder.overTime) {
            // 最后一次添加的数据已经超时
            this.clear();
        } else if(timeMap.size() > builder.thresholdSize) {
            // 容量达到阈值时,需要检查
            valueMap.keySet().forEach(v -> {
                if(System.currentTimeMillis() - timeMap.get(v) > builder.overTime) {
                    // 超时数据为僵尸数据,满足删除的条件
                    this.remove(v);
                }
            });
        }
    }

    /** 新增 */
    public void put(K key, V value) {
        if(key == null || value == null) {
            // ConcurrentHashMap 不可以存储 null 值
            return;
        }
        lastTime = System.currentTimeMillis();
        // 检查数据是否过期
        this.checkAndCleanStaleData();
        timeMap.put(key, System.currentTimeMillis());
        valueMap.put(key, value);
    }

    /** 删除 */
    public V remove(K key) {
        timeMap.remove(key);
        return valueMap.remove(key);
    }

    /** 获取 */
    public V get(K key) {
        this.checkAndCleanStaleData();
        return this.getOrDefault(key, null);
    }

    /** 获取 */
    public V getOrDefault(K key, V defaultValue) {
        return (timeMap.get(key) == null || System.currentTimeMillis() - timeMap.get(key) > builder.overTime) ? null : valueMap.getOrDefault(key, defaultValue);
    }

    /** 判断是否存在key */
    public boolean containsKey(K key) {
        return this.get(key) != null;
    }

    public void clear() {
        timeMap.clear();
        valueMap.clear();
    }

    public Set<K> keySet() {
        this.checkAndCleanStaleData();
        return valueMap.keySet();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        this.checkAndCleanStaleData();
        return valueMap.entrySet();
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        this.checkAndCleanStaleData();
        valueMap.forEach(action);
    }

}