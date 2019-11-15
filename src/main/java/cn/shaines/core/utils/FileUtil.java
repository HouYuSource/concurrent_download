package cn.shaines.core.utils;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author houyu
 * @createTime 2019/10/5 15:09
 */
public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    /**
     * 获取文件目录
     */
    public static String getFilePath(String path) {
        int fileIndex = path.replaceAll("\\\\", "/").lastIndexOf("/");
        return path.substring(0, fileIndex);
    }

    /**
     * 获取文件的类型
     */
    public static String getFileType(String s) {
        int indexOf;
        if((indexOf = s.indexOf("?")) > -1) {
            // 兼容URL类型的文件
            s = s.substring(0, indexOf);
        }
        String type = s.substring(s.lastIndexOf(".") + 1);
        return s.equals(type) ? "" : type;
    }

    /**
     * 获取文件名(去除类型名称) "D:/a/b.txt"  ==>> "D:/a/b"
     */
    public static String getNameWithoutExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf("."));
    }


    /**
     * 目录下已经有同名文件, 则文件重命名, 增加文件序号
     * use newFileName("D:/a", "t2.txt")
     */
    public static String newFileName(String path, String filename) {
        String newFileName = "";
        String withoutExt;
        File curFile = new File(path + "/" + filename);
        if (curFile.exists()) {
            for (int counter = 1; curFile.exists(); counter++) {
                withoutExt = getNameWithoutExtension(curFile.getName());
                if (withoutExt.endsWith(counter - 1 + ")")) {
                    withoutExt = withoutExt.substring(0, withoutExt.indexOf("("));
                }
                newFileName = withoutExt + "(" + counter + ")" + "." + getFileType(curFile.getName());
                curFile = new File(path + "/" + newFileName);
            }
        } else {
            newFileName = curFile.getName();
        }
        return newFileName;
    }

    /**
     * 判断是否是图片类型文件
     */
    public static boolean isImgFile(String type) {
        type = "." + type;
        return ".bmp.jpeg.gif.psd.png.tiff.tga.eps.BMP.JPEG.GIF.PSD.PNG.TIFF.TGA.EPS".contains(type);
    }

    /**
     * 获取模糊的图片类型
     */
    public static String getVagueImgFileType(String uri) {
        String[] split = "bmp.jpeg.gif.psd.png.tiff.tga.eps.BMP.JPEG.GIF.PSD.PNG.TIFF.TGA.EPS".split("\\.");
        for(String tempType : split) {
            if(uri.contains(tempType)) {
                return tempType;
            }
        }
        return null;
    }

}