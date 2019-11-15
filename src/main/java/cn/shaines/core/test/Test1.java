package cn.shaines.core.test;

import cn.shaines.core.utils.ConcurrentDownLoad;
import java.io.IOException;

/**
 * @author houyu
 * @createTime 2019/11/11 9:39
 */
public class Test1 {


    public static void main(String[] args) throws IOException {

        // ConcurrentDownLoad downLoad = new ConcurrentDownLoad(5, 1000,
        // http://117.148.175.41/cdn/pcfile/20190904/16/58/GeePlayerSetup_app.exe?dis_k=26cbb7b142c2446397843d6543da209ae&dis_t=1573620667&dis_dz=CMNET-GuangDong&dis_st=36
        //                                                      // "https://v-78e897c1.71edge.com/videos/other/20191031/6c/b1/b66a3a53315818778b55f4a71ea66851.mp4",
        //                                                      // "C:\\Users\\houyu\\Desktop\\aaaaa.mp4"
        //                                                      "http://117.148.175.41/cdn/pcfile/20190904/16/58/GeePlayerSetup_app.exe?dis_k=26cbb7b142c2446397843d6543da209ae&dis_t=1573620667&dis_dz=CMNET-GuangDong&dis_st=36",
        //                                                      "C:\\Users\\houyu\\Desktop\\GeePlayerSetup_app_my.exe"
        // );
        // System.out.println(387925626 / 1120931452);

        ConcurrentDownLoad.builder()
                // 设置URL
                .setUrl("https://v-78e897c1.71edge.com/videos/other/20191031/6c/b1/b66a3a53315818778b55f4a71ea66851.mp4")
                // 设置线程每次请求的大小(1M)
                .setBlockSize(1024)
                // 设置线程数量
                .setThreadCount(5)
                // 设置保存路径
                .setPath("C:\\Users\\houyu\\Desktop\\851.mp4")
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
    }

}
