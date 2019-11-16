package cn.shaines.concurrent.download;

import cn.shaines.core.utils.ConcurrentDownLoad;
import cn.shaines.core.utils.PropertiesUtil;

/**
 * @author houyu
 * @createTime 2019/11/15 16:16
 */
public class AppMain {

    public static void main(String[] args) {
        PropertiesUtil propertiesUtil = PropertiesUtil.builder(System.getProperty("user.dir") + "/download.txt").build();
        // PropertiesUtil propertiesUtil = PropertiesUtil.builder("D:\\develop\\workspace\\idea\\java_all\\hatch\\concurrent_download\\src\\main\\resources\\download.properties").build();
        //
        String url = propertiesUtil.getPropertyCastType("url", String.class);
        String path = propertiesUtil.getPropertyCastType("path", String.class);
        int threadCount = propertiesUtil.getPropertyOrDefault("threadCount", 5);
        threadCount = threadCount > 10 || threadCount <= 1 ? 5 : threadCount;
        long blockSize = propertiesUtil.getPropertyOrDefault("blockSize", 5242880);
        blockSize = (blockSize < 1024 * 1024 || blockSize > 5242880) ? 5242880 : blockSize;
        boolean deleteIfExist = propertiesUtil.getPropertyOrDefault("deleteIfExist", false);
        boolean keepOnIfDisconnect = propertiesUtil.getPropertyOrDefault("keepOnIfDisconnect", true);
        //
        if(!path.contains("\\") && !path.contains("/")) {
            path = System.getProperty("user.dir") + "/下载目录/" + path;
        }
        //
        ConcurrentDownLoad.builder()
                // 设置URL
                .setUrl(url)
                // 设置线程每次请求的大小(1M)
                .setBlockSize(blockSize)
                // 设置线程数量
                .setThreadCount(threadCount)
                // 设置保存路径
                .setPath(path)
                // 设置存在是否删除(如果设置 setKeepOnIfDisconnect(true) 则失效)
                .setDeleteIfExist(deleteIfExist)
                // 是否支持断点下载
                .setKeepOnIfDisconnect(keepOnIfDisconnect)
                // 创建
                .build()
                // 开始
                .start((msg, total, current, speed) -> {
                    // 下载回调方法
                    // log.debug("msg:{} total:{} current:{} speed:{} KB/s, 已下载进度:{}", msg, total, current, speed / 1024, format.format(total == 0 ? 0 : (float) current / (float) total));
                });
    }

}
